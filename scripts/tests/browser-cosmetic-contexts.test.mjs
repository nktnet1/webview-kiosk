import assert from "node:assert/strict";
import { createServer } from "node:http";
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import { before, after, test } from "node:test";

const options = { skip: !process.env.SCRIPTLET_CHROMIUM };
const script = readFileSync(
  new URL("../../app/src/main/assets/content-blocking/cosmetic.js", import.meta.url),
  "utf8",
);
let browser, server, base;
before(async () => {
  if (options.skip) return;
  const { chromium } = createRequire(import.meta.url)("playwright");
  browser = await chromium.launch({
    executablePath: process.env.SCRIPTLET_CHROMIUM,
    headless: true,
    args: ["--no-sandbox"],
  });
  server = createServer((request, response) => {
    response.writeHead(200, { "Content-Type": "text/html" });
    response.end("<!doctype html><html><head></head><body></body></html>");
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  base = "http://127.0.0.1:" + server.address().port;
});
after(async () => {
  await browser?.close();
  if (server) await new Promise((resolve) => server.close(resolve));
});

function procedure(selector, steps, action = ["hide", ""]) {
  return {
    selector, text: null, upward: 0, remove: false, style: null,
    removeAttribute: null, removeClass: null, upwardSelector: null,
    operations: JSON.stringify({ selector, steps, action }),
  };
}

async function cosmeticPage(procedures) {
  const context = await browser.newContext();
  await context.addInitScript({ content: `
    window.observedRoots = new Map();
    window.nativeAttachShadow = Element.prototype.attachShadow;
    window.activeBlocking = true;
    const Observer = MutationObserver;
    window.MutationObserver = class extends Observer {
      observe(root, options) {
        if (options.characterData && options.attributes) {
          if (!observedRoots.has(this)) observedRoots.set(this, new Set());
          observedRoots.get(this).add(root);
        }
        return super.observe(root, options);
      }
      disconnect() { observedRoots.delete(this); return super.disconnect(); }
    };
    window.wkCosmeticRules = { postMessage(raw) {
      const query = JSON.parse(raw);
      queueMicrotask(() => this.onmessage({ data: JSON.stringify({
        id: query.id, active: activeBlocking, selectors: [],
        procedures: ${JSON.stringify(procedures)}
      }) }));
    } };
    ${script}
  ` });
  const page = await context.newPage();
  await page.goto(base);
  return { context, page };
}

test("relative XPath updates the selected context and observes sibling dependencies", options,
  async () => {
    const { context, page } = await cosmeticPage([
      procedure(".card", [["xpath", ".//span[@data-kind='ad']"]]),
      procedure(".marker", [["xpath", "../div[@data-kind='promo']"]]),
    ]);
    try {
      await page.evaluate(() => {
        document.body.innerHTML =
          '<section class="card"><span id="ad" data-kind="ad">Ad</span></section>' +
          '<span id="outside" data-kind="ad">Outside</span>' +
          '<section><i class="marker"></i><div id="sibling" data-kind="promo">Ad</div>' +
          '</section>';
      });
      await page.waitForFunction(() =>
        getComputedStyle(document.getElementById("ad")).display === "none" &&
        getComputedStyle(document.getElementById("sibling")).display === "none");
      assert.notEqual(await page.locator("#outside").evaluate(
        (node) => getComputedStyle(node).display), "none");
      await page.evaluate(() => {
        document.getElementById("ad").setAttribute("data-kind", "article");
        document.getElementById("sibling").setAttribute("data-kind", "article");
      });
      await page.waitForFunction(() =>
        getComputedStyle(document.getElementById("ad")).display !== "none" &&
        getComputedStyle(document.getElementById("sibling")).display !== "none");
      assert.equal(await page.evaluate(() => Array.from(observedRoots.values()).some(
        (roots) => Array.from(roots).some((root) => root === document.querySelector(".card")))),
      true);
    } finally { await context.close(); }
  });

test("open shadow roots support late attachment, native getters, styles, and cleanup", options,
  async () => {
    const { context, page } = await cosmeticPage([
      procedure(".host", [["shadow", ".ad"], ["text", "Sponsored"]]),
      procedure(".host", [["shadow", ".colour"]], ["style-action", "color: rgb(1, 2, 3)"]),
    ]);
    try {
      await page.evaluate(() => {
        window.host = document.createElement("section");
        host.className = "host";
        document.body.appendChild(host);
        window.closedHost = document.createElement("section");
        closedHost.className = "host";
        window.closedRoot = closedHost.attachShadow({ mode: "closed" });
        closedRoot.innerHTML = '<div class="ad">Sponsored</div>';
        document.body.appendChild(closedHost);
      });
      // Let the runner record the matching host before its shadow root exists.
      await page.waitForTimeout(160);
      await page.evaluate(() => {
        window.openRoot = host.attachShadow({ mode: "open" });
        openRoot.innerHTML = '<div class="ad" style="display: grid">Sponsored</div>' +
          '<div class="colour">Colour</div>';
        window.getterCalls = 0;
        Object.defineProperty(host, "shadowRoot", {
          get() { getterCalls++; throw Error("getter"); },
        });
      });
      await page.waitForFunction(() =>
        getComputedStyle(openRoot.querySelector(".ad")).display === "none" &&
        getComputedStyle(openRoot.querySelector(".colour")).color === "rgb(1, 2, 3)");
      assert.equal(await page.evaluate(() => getterCalls), 0);
      assert.notEqual(await page.evaluate(() =>
        getComputedStyle(closedRoot.querySelector(".ad")).display), "none");
      assert.equal(await page.evaluate(() => Array.from(observedRoots.values()).some(
        (roots) => roots.has(closedRoot))), false);
      await page.evaluate(() => { openRoot.querySelector(".ad").style.display = "flex"; });
      await page.waitForTimeout(100);
      await page.evaluate(() => { openRoot.querySelector(".ad").firstChild.data = "Article"; });
      await page.waitForFunction(() =>
        getComputedStyle(openRoot.querySelector(".ad")).display === "flex");
      await page.evaluate(() => { openRoot.querySelector("style").remove(); });
      await page.waitForFunction(() =>
        getComputedStyle(openRoot.querySelector(".colour")).color === "rgb(1, 2, 3)");
      await page.evaluate(() => { host.remove(); });
      await page.waitForFunction(() => !Array.from(observedRoots.values()).some(
        (roots) => roots.has(openRoot)));
      assert.equal(await page.evaluate(() => openRoot.querySelector("style")), null);
    } finally { await context.close(); }
  });

test("template content matches while detached and watches only reached fragments", options,
  async () => {
    const { context, page } = await cosmeticPage([
      procedure(".card", [["has", {
        selector: "template", steps: [["content", ".label"], ["text", "Sponsored"]],
      }]]),
      procedure("template.prune", [["content", ".ad"]], ["remove", ""]),
      procedure(".fake", [["content", ".ad"]], ["remove", ""]),
    ]);
    try {
      await page.evaluate(() => {
        document.body.innerHTML =
          '<div id="card" class="card"><template id="template">' +
          '<span class="label">Sponsored</span></template></div>' +
          '<template id="prune" class="prune"><span class="ad">Ad</span>' +
          '<span class="keep">Article</span></template><template id="unrelated"></template>' +
          '<div class="fake"></div>';
        window.fragment = document.getElementById("template").content;
        window.pruned = document.getElementById("prune").content;
        window.getterCalls = 0;
        for (const node of [document.getElementById("template"), document.querySelector(".fake")]) {
          Object.defineProperty(node, "content", {
            get() { getterCalls++; throw Error("getter"); },
          });
        }
      });
      await page.waitForFunction(() =>
        getComputedStyle(document.getElementById("card")).display === "none" &&
        pruned.querySelector(".ad") === null);
      assert.equal(await page.evaluate(() => pruned.querySelector(".keep").textContent), "Article");
      assert.equal(await page.evaluate(() => getterCalls), 0);
      assert.equal(await page.evaluate(() => Array.from(observedRoots.values()).some(
        (roots) => roots.has(document.getElementById("unrelated").content))), false);
      await page.evaluate(() => { fragment.querySelector(".label").firstChild.data = "Article"; });
      await page.waitForFunction(() =>
        getComputedStyle(document.getElementById("card")).display !== "none");
      await page.evaluate(() => {
        fragment.querySelector(".label").firstChild.data = "Sponsored";
        pruned.appendChild(Object.assign(document.createElement("span"), { className: "ad" }));
      });
      await page.waitForFunction(() =>
        getComputedStyle(document.getElementById("card")).display === "none" &&
        pruned.querySelector(".ad") === null);
      await page.evaluate(() => { document.getElementById("template").remove(); });
      await page.waitForFunction(() =>
        getComputedStyle(document.getElementById("card")).display !== "none" &&
        !Array.from(observedRoots.values()).some((roots) => roots.has(fragment)));
    } finally { await context.close(); }
  });

test("nested shadow/template rules resume after pagehide and release observers on disable", options,
  async () => {
    const { context, page } = await cosmeticPage([
      procedure(".host", [["shadow", ".card"], ["has", {
        selector: "template", steps: [["content", ".ad"]],
      }]]),
    ]);
    try {
      await page.evaluate(() => {
        window.host = document.createElement("div");
        host.className = "host";
        window.root = host.attachShadow({ mode: "open" });
        root.innerHTML = '<div class="card" style="display: grid"><template>' +
          '<span class="ad">Ad</span></template></div>';
        window.card = root.querySelector(".card");
        window.fragment = root.querySelector("template").content;
        document.body.appendChild(host);
      });
      await page.waitForFunction(() => card.style.display === "none");
      await page.evaluate(() => { card.style.display = "flex"; });
      await page.waitForTimeout(100);
      await page.evaluate(() => { dispatchEvent(new PageTransitionEvent("pagehide")); });
      assert.equal(await page.evaluate(() => observedRoots.size), 0);
      await page.evaluate(() => {
        fragment.querySelector(".ad").className = "article";
        dispatchEvent(new PageTransitionEvent("pageshow"));
      });
      await page.waitForFunction(() => card.style.display === "flex");
      await page.evaluate(() => {
        activeBlocking = false;
        document.body.appendChild(Object.assign(document.createElement("div"), { id: "disable" }));
      });
      await page.waitForFunction(() => observedRoots.size === 0);
      assert.equal(await page.evaluate(() => Element.prototype.attachShadow === nativeAttachShadow),
        true);
      assert.equal(await page.evaluate(() => root.querySelector("style")), null);
      assert.equal(await page.evaluate(() => fragment.querySelector("style")), null);
    } finally { await context.close(); }
  });


test("inert template results never leak reversible effects into clones", options, async () => {
  // The compiler rejects these direct hide/style forms. Defend restored programs too.
  const { context, page } = await cosmeticPage([
    procedure("template", [["content", ".ad"]]),
    procedure("template", [["content", ".ad"]], ["style-action", "display: none"]),
  ]);
  try {
    await page.evaluate(() => {
      document.body.innerHTML =
        '<template><span class="ad" style="display: grid">Article</span></template>';
      window.fragment = document.querySelector("template").content;
    });
    await page.waitForFunction(() => Array.from(observedRoots.values()).some(
      (roots) => roots.has(fragment)));
    assert.equal(await page.evaluate(() => fragment.querySelector(".ad").style.display), "grid");
    assert.equal(await page.evaluate(() => fragment.querySelector("style")), null);
    await page.evaluate(() => {
      document.body.appendChild(fragment.cloneNode(true));
      activeBlocking = false;
      document.body.appendChild(Object.assign(document.createElement("div"), { id: "disable" }));
    });
    await page.waitForFunction(() => observedRoots.size === 0);
    assert.equal(await page.locator("body > .ad").evaluate(
      (node) => getComputedStyle(node).display), "grid");
    assert.equal(await page.locator("body > .ad").evaluate(
      (node) => node.getAttributeNames().some((name) => name.startsWith("data-wk-cosmetic-"))),
    false);
    assert.equal(await page.locator("style").count(), 0);
  } finally { await context.close(); }
});
