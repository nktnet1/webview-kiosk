// Run the shipped page script with a small DOM/bridge fixture, without browser dependencies.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { runInNewContext } from "node:vm";

const script = readFileSync(new URL("../../app/src/main/assets/content-blocking/cosmetic.js", import.meta.url), "utf8");

function page({ protocol = "https:", hostname = "publisher.example", bridgeAvailable = true, elements = [] } = {}) {
  const messages = [];
  const timers = new Map();
  const events = new Map();
  const observers = [];
  let nextTimer = 0;
  class Element {
    constructor(tag, id = "", classes = []) {
      this.nodeType = 1;
      this.tag = tag;
      this.id = id;
      this.classList = classes;
      this.children = [];
      this.parent = null;
      if (tag === "style") this.sheet = {
        cssRules: [],
        insertRule(rule) { this.cssRules.push(rule); },
      };
    }
    get isConnected() { return this.parent !== null; }
    contains(other) { return this === other || this.children.some(child => child.contains(other)); }
    appendChild(child) { this.children.push(child); child.parent = this; return child; }
    remove() {
      this.parent.children = this.parent.children.filter(child => child !== this);
      this.parent = null;
    }
    matches(selector) {
      // This fixture checks that the script honors the browser's parser rejection;
      // actual CSS syntax/compatibility still needs a WebView device test.
      if (selector.startsWith("@") || selector.includes("{") || selector.includes(":has-text(")) {
        throw new SyntaxError("Invalid CSS selector");
      }
      return false;
    }
  }
  const root = new Element("html");
  const head = root.appendChild(new Element("head"));
  const body = root.appendChild(new Element("body"));
  const document = {
    nodeType: 9,
    children: [root],
    head,
    documentElement: root,
    contains: other => other === document || root.contains(other),
    createElement: tag => new Element(tag),
    createTreeWalker(node) {
      const descendants = [];
      function walk(parent) { for (const child of parent.children) { descendants.push(child); walk(child); } }
      walk(node);
      let index = 0;
      return { nextNode: () => descendants[index++] || null };
    },
  };
  for (const element of elements) body.appendChild(new Element("div", element.id, element.classes));
  const bridge = { postMessage: value => messages.push(JSON.parse(value)) };
  const window = {
    addEventListener(name, callback) { events.set(name, callback); },
    ...(bridgeAvailable ? { wkCosmeticRules: bridge } : {}),
  };
  const context = {
    window, document, location: { protocol, hostname, href: `${protocol}//${hostname}/article` },
    setTimeout(callback) { const id = ++nextTimer; timers.set(id, callback); return id; },
    clearTimeout(id) { timers.delete(id); },
    MutationObserver: class {
      constructor(callback) { this.callback = callback; observers.push(this); }
      observe() { this.active = true; }
      disconnect() { this.active = false; }
    },
  };
  function flush() {
    const first = timers.entries().next().value;
    assert.ok(first, "Expected scheduled work");
    timers.delete(first[0]);
    first[1]();
  }
  function mutate(record) { for (const observer of observers) if (observer.active) observer.callback([record]); }
  return {
    messages, timers, observers, body, head, document,
    run: () => runInNewContext(script, context),
    flush,
    mutate,
    add(id, classes = []) {
      const node = body.appendChild(new Element("div", id, classes));
      mutate({ type: "childList", addedNodes: [node] });
      return node;
    },
    reply(data) { bridge.onmessage({ data: JSON.stringify({ id: messages.at(-1).id, active: true, ...data }) }); },
    event: name => events.get(name)(),
    styles: () => head.children.filter(node => node.tag === "style"),
  };
}

test("only HTTP pages with a bridge start the scanner, once per document", () => {
  for (const options of [
    { protocol: "file:" }, { protocol: "blob:" }, { bridgeAvailable: false },
    { hostname: "appassets.androidplatform.net" }, { hostname: "pdf-dummy.webviewkiosk.nktnet.uk" },
  ]) {
    const p = page(options);
    p.run();
    assert.equal(p.observers.length, 0);
    assert.equal(p.timers.size, 0);
  }
  const p = page();
  p.run();
  p.run();
  assert.equal(p.observers.length, 1);
  p.flush();
  assert.equal(p.messages.length, 1);
  assert.equal(p.messages[0].url, "https://publisher.example/article");
});

test("CSS hides are deduplicated and invalid selectors cannot inject declarations", () => {
  const p = page();
  p.run();
  p.flush();
  p.reply({ selectors: [".ad", ".ad", "@import url(https://evil.example)", ".ad{color:red}", ".ad:has-text(Ads)", "#sponsor"] });
  assert.deepEqual(p.styles()[0].sheet.cssRules, [".ad{display:none!important}", "#sponsor{display:none!important}"]);
});

test("new classes and IDs are queried without resending already observed tokens", () => {
  const p = page({ elements: [{ id: "sponsor", classes: ["ad", "ad"] }] });
  p.run();
  p.flush();
  assert.deepEqual(p.messages[0].classes, ["ad"]);
  assert.deepEqual(p.messages[0].ids, ["sponsor"]);
  p.reply({ selectors: [".ad"] });
  const node = p.add("next", ["ad", "new-ad"]);
  p.flush();
  assert.deepEqual(p.messages[1].classes, ["new-ad"]);
  assert.deepEqual(p.messages[1].ids, ["next"]);
  p.reply({ selectors: [] });
  node.classList.push("later");
  p.mutate({ type: "attributes", target: node });
  p.flush();
  assert.deepEqual(p.messages[2].classes, ["later"]);
});

test("large pages use bounded batches and wait for replies before querying again", () => {
  const p = page({ elements: Array.from({ length: 900 }, (_, i) => ({ id: `id-${i}`, classes: [`class-${i}`] })) });
  p.run();
  p.flush();
  assert.equal(p.messages[0].classes.length, 256);
  p.flush();
  p.flush();
  assert.equal(p.messages.length, 1);
  p.reply({ selectors: [] });
  for (let i = 0; i < 3; i++) { p.flush(); p.reply({ selectors: [] }); }
  assert.equal(p.messages.length, 4);
  assert.equal(p.messages.reduce((sum, message) => sum + message.classes.length, 0), 900);
  assert.equal(p.messages.reduce((sum, message) => sum + message.ids.length, 0), 900);
  assert.equal(p.timers.size, 0);
});

test("backpressure retries retain tokens and unrelated replies are ignored", () => {
  const p = page({ elements: [{ id: "sponsor", classes: ["ad"] }] });
  p.run();
  p.flush();
  p.reply({ id: 999, selectors: [".wrong"] });
  assert.equal(p.styles().length, 0);
  p.reply({ retry: true });
  p.flush();
  assert.deepEqual(p.messages[1].classes, ["ad"]);
  assert.deepEqual(p.messages[1].ids, ["sponsor"]);
  p.reply({ selectors: [".ad"] });
  assert.equal(p.styles()[0].sheet.cssRules.length, 1);
});

test("a disabled engine stops observing and removes existing cosmetic rules", () => {
  const p = page();
  p.run();
  p.flush();
  p.reply({ selectors: [".ad"] });
  p.add("new-ad");
  p.flush();
  p.reply({ active: false });
  assert.equal(p.styles().length, 0);
  assert.equal(p.observers[0].active, false);
  p.add("another-ad");
  assert.equal(p.timers.size, 0);
});

test("removed styles are restored without another native query", () => {
  const p = page();
  p.run();
  p.flush();
  p.reply({ selectors: [".ad"] });
  p.styles()[0].remove();
  p.mutate({ type: "childList", addedNodes: [] });
  p.flush();
  assert.deepEqual(p.styles()[0].sheet.cssRules, [".ad{display:none!important}"]);
  assert.equal(p.messages.length, 1);
});

test("pagehide cancels queued work and pageshow resumes scanning", () => {
  const p = page();
  p.run();
  p.flush();
  p.reply({ selectors: [] });
  p.add("new-ad");
  p.event("pagehide");
  assert.equal(p.observers[0].active, false);
  assert.equal(p.timers.size, 0);
  p.event("pageshow");
  p.flush();
  assert.deepEqual(p.messages[1].ids, ["new-ad"]);
  assert.equal(p.observers[0].active, true);
});

test("oversized and control-character tokens are skipped before the bridge", () => {
  const p = page({ elements: [{ id: "invalid\n", classes: ["a".repeat(129), "good"] }] });
  p.run();
  p.flush();
  assert.deepEqual(p.messages[0].ids, []);
  assert.deepEqual(p.messages[0].classes, ["good"]);
});
