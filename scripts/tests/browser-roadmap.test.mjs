import assert from "node:assert/strict";
import { createServer } from "node:http";
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import { before, after, test } from "node:test";
import { page as fixture } from "./scriptlet-fixture.mjs";

const options = { skip: !process.env.SCRIPTLET_CHROMIUM };
const asset = (name) =>
  readFileSync(
    new URL("../../app/src/main/assets/content-blocking/" + name, import.meta.url),
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
  server = createServer((req, res) => {
    res.writeHead(200, { "Content-Type": "text/html" });
    res.end('<!doctype html><body><main id="content">Article</main></body>');
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
    selector,
    text: null,
    upward: 0,
    remove: false,
    style: null,
    removeAttribute: null,
    removeClass: null,
    upwardSelector: null,
    operations: JSON.stringify({ selector, steps, action }),
  };
}
async function cosmeticPage(procedures) {
  const context = await browser.newContext();
  await context.addInitScript({
    content: `
        window.wkCosmeticRules = { postMessage(raw) {
            const query = JSON.parse(raw);
            queueMicrotask(() => this.onmessage({ data: JSON.stringify({
                id: query.id, active: true, selectors: [],
                procedures: ${JSON.stringify(procedures)}
            }) }));
        } };
        ${asset("cosmetic.js")}
    `,
  });
  const page = await context.newPage();
  await page.goto(base);
  return { context, page };
}

test(
  "procedural hides respond to text changes and restore author styles without document queries",
  options,
  async () => {
    const { context, page } = await cosmeticPage([
      procedure(".card", [["text", "Advertisement"]]),
    ]);
    try {
      await page.evaluate(() => {
        const card = document.createElement("div");
        card.id = "ad";
        card.className = "card";
        card.style.display = "grid";
        card.textContent = "Advertisement";
        document.body.appendChild(card);
      });
      await page.waitForFunction(
        () => getComputedStyle(document.getElementById("ad")).display === "none",
      );
      await page.evaluate(() => {
        document.querySelectorAll = () => {
          throw new Error("Full-document query");
        };
        const walker = document.createTreeWalker.bind(document);
        window.walks = 0;
        document.createTreeWalker = (...args) => {
          walks++;
          return walker(...args);
        };
        document.getElementById("ad").firstChild.data = "Article";
      });
      await page.waitForFunction(
        () => getComputedStyle(document.getElementById("ad")).display === "grid",
      );
      assert.equal(await page.evaluate(() => walks), 0);
    } finally {
      await context.close();
    }
  },
);

test(
  "nested predicates, sibling chains, and ancestor cleanup use native DOM",
  options,
  async () => {
    const { context, page } = await cosmeticPage([
      procedure(
        ".heading",
        [
          ["text", "Ads"],
          ["css", " + ul"],
        ],
        ["remove", ""],
      ),
      procedure(".card", [
        ["has", { selector: "> .label", steps: [["text", "/sponsored/i"]] }],
        ["upward", "1"],
      ]),
      procedure(".attribute", [
        ["attr", "data-kind", "advert"],
        ["length", "3"],
      ]),
    ]);
    try {
      await page.evaluate(() => {
        document.body.insertAdjacentHTML(
          "beforeend",
          '<h2 class="heading">Ads</h2>' +
            '<ul id="list"><li>Offer</li></ul>' +
            '<section id="outer"><div class="card" id="seed">' +
            '<span class="label">Sponsored</span></div></section>' +
            '<div class="attribute" id="attr" data-kind="advert">Offer</div>',
        );
      });
      await page.waitForFunction(
        () =>
          !document.getElementById("list") &&
          getComputedStyle(document.getElementById("outer")).display === "none" &&
          getComputedStyle(document.getElementById("attr")).display === "none",
      );
      await page.evaluate(() => {
        document.getElementById("seed").remove();
        document.getElementById("attr").setAttribute("data-kind", "article");
      });
      await page.waitForFunction(
        () =>
          getComputedStyle(document.getElementById("outer")).display !== "none" &&
          getComputedStyle(document.getElementById("attr")).display !== "none",
      );
    } finally {
      await context.close();
    }
  },
);

test(
  "page request hooks filter native APIs in the actual calling frame",
  options,
  async () => {
    const context = await browser.newContext();
    await context.addInitScript({
      content: `
        window.policyCalls = [];
        window.wkRequestRules = { shouldBlock(...args) {
            policyCalls.push(args); return args[0].includes('/blocked');
        } };
        ${asset("requests.js")}
    `,
    });
    const page = await context.newPage();
    await page.goto(base);
    try {
      const result = await page.evaluate(() => {
        let error;
        try {
          new WebSocket("/blocked");
        } catch (e) {
          error = e.name;
        }
        return {
          error,
          beacon: navigator.sendBeacon("/blocked", "payload"),
          calls: policyCalls,
        };
      });
      assert.equal(result.error, "SecurityError");
      assert.equal(result.beacon, true);
      assert.equal(result.calls[0][0], base.replace("http:", "ws:") + "/blocked");
      await page.evaluate((url) => {
        const frame = document.createElement("iframe");
        frame.src = url;
        document.body.appendChild(frame);
      }, base + "/frame");
      await page.waitForFunction(
        () => frames.length && frames[0].location.pathname === "/frame",
      );
      const child = page.frames().find((frame) => frame.url().endsWith("/frame"));
      assert.equal(
        await child.evaluate(() => navigator.sendBeacon("/blocked", "payload")),
        true,
      );
      assert.equal(await child.evaluate(() => policyCalls[0][1]), base);
    } finally {
      await context.close();
    }
  },
);

test(
  "AdsByGoogle shim fills late placeholders using subtree work and original iframe load events",
  options,
  async () => {
    const context = await browser.newContext();
    await context.addInitScript({
      content: fixture([["RESOURCE", ["googlesyndication_adsbygoogle.js"], "127.0.0.1"]], {
        host: "127.0.0.1",
      }).source,
    });
    const page = await context.newPage();
    await page.goto(base);
    try {
      await page.evaluate(() => {
        document.querySelectorAll = () => {
          throw new Error("Full-document scan");
        };
        const node = document.createElement("ins");
        node.className = "adsbygoogle";
        node.id = "placeholder";
        document.body.appendChild(node);
      });
      await page.waitForFunction(
        () => document.getElementById("placeholder").dataset.adStatus === "filled",
      );
      assert.equal(
        await page.evaluate(
          () => document.getElementById("placeholder").firstChild.dataset.loadComplete,
        ),
        "true",
      );
      assert.equal(await page.evaluate(() => adsbygoogle.loaded), true);
    } finally {
      await context.close();
    }
  },
);

test(
  "IMA DAI shim ignores reset requests and preserves event and stream-response lifecycles",
  options,
  async () => {
    const context = await browser.newContext();
    const source = fixture([["TRUSTED_RESOURCE", ["google-ima-dai.js"], "127.0.0.1"]], {
      host: "127.0.0.1",
    }).source;
    await context.addInitScript({
      content: `
            window.streamRequests = [];
            window.fetch = (url, options) => new Promise(resolve => {
                streamRequests.push({ url, options, resolve });
            });
            ${source}
        `,
    });
    const page = await context.newPage();
    await page.goto(base);
    try {
      await page.evaluate(() => {
        const api = google.ima.dai.api;
        window.manager = new api.StreamManager(document.createElement("video"));
        window.events = [];
        manager.addEventListener(api.StreamEvent.Type.LOADED, (event) => {
          events.push(event.getStreamData());
        });
        window.request = new api.VODStreamRequest();
        request.contentSourceId = "content";
        request.videoId = "video";
        manager.requestStream(request);
        manager.reset();
        streamRequests[0].resolve(
          new Response(
            JSON.stringify({
              stream_manifest: "https://video.example/obsolete.m3u8",
              stream_id: "obsolete",
            }),
          ),
        );
      });
      await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 20)));
      assert.equal(await page.evaluate(() => events.length), 0);
      await page.evaluate(() => {
        manager.requestStream(request);
        streamRequests[1].resolve(
          new Response(
            JSON.stringify({
              stream_manifest: "https://video.example/content.m3u8",
              stream_id: "current",
            }),
          ),
        );
      });
      await page.waitForFunction(() => events.length === 1);
      assert.equal(
        await page.evaluate(() => events[0].url),
        "https://video.example/content.m3u8",
      );
      assert.equal(await page.evaluate(() => events[0].streamId), "current");
      assert.equal(await page.evaluate(() => streamRequests[0].options.method), "POST");
    } finally {
      await context.close();
    }
  },
);

test(
  "XML XPath pruning removes attributes and elements without replacing the document",
  options,
  async () => {
    const context = await browser.newContext();
    await context.addInitScript({
      content: fixture(
        [
          ["XML_PRUNE", ["xpath(//Ad/@id)", "", ""], "127.0.0.1"],
          ["XML_PRUNE", ["xpath(//Ad)", "", ""], "127.0.0.1"],
        ],
        { host: "127.0.0.1" },
      ).source,
    });
    await context.route("**/manifest.xml", (route) =>
      route.fulfill({
        contentType: "text/xml",
        body: '<MPD><Ad id="advert"/><Content id="kept"/></MPD>',
      }),
    );
    const page = await context.newPage();
    await page.goto(base);
    try {
      const text = await page.evaluate(async () => (await fetch("/manifest.xml")).text());
      assert.equal(text.includes("<Ad"), false);
      assert.equal(text.includes("kept"), true);
    } finally {
      await context.close();
    }
  },
);

test(
  "element object edits visit late elements once and native method hooks preserve their receiver",
  options,
  async () => {
    const context = await browser.newContext();
    const steps = [[false, ["ads"], null]];
    const remove = JSON.stringify([false, steps, [], null]);
    const assign = JSON.stringify([false, steps, [], ["=", false]]);
    const call = JSON.stringify([
      false,
      [],
      [],
      ["call", ["${val}", "setAttribute", "data-edited", "yes"]],
    ]);
    await context.addInitScript({
      content: fixture(
        [
          ["EDIT_ELEMENT_OBJECT", [".ad", "", remove, "500"], "127.0.0.1"],
          ["TRUSTED_EDIT_ELEMENT_OBJECT", [".banner", "", call, "500"], "127.0.0.1"],
          [
            "TRUSTED_EDIT_INBOUND_ELEMENT",
            ["Element.prototype.setAttribute", "this", ".native", "", assign],
            "127.0.0.1",
          ],
        ],
        { host: "127.0.0.1" },
      ).source,
    });
    const page = await context.newPage();
    await page.goto(base);
    try {
      await page.evaluate(() => {
        for (const name of ["ad", "banner", "native"]) {
          const node = document.createElement("div");
          node.id = name;
          node.className = name;
          node.ads = true;
          document.body.appendChild(node);
        }
        document.getElementById("native").setAttribute("data-native", "preserved");
      });
      await page.waitForFunction(
        () =>
          !Object.hasOwn(document.getElementById("ad"), "ads") &&
          document.getElementById("banner").getAttribute("data-edited") === "yes",
      );
      assert.equal(await page.evaluate(() => document.getElementById("native").ads), false);
      assert.equal(
        await page.evaluate(() =>
          document.getElementById("native").getAttribute("data-native"),
        ),
        "preserved",
      );
    } finally {
      await context.close();
    }
  },
);

test(
  "computed-style predicates ignore hides and track stylesheet changes",
  options,
  async () => {
    const { context, page } = await cosmeticPage([
      procedure(".style-card", [["style", "display", "grid", ""]]),
    ]);
    try {
      await page.evaluate(() => {
        document.body.insertAdjacentHTML(
          "beforeend",
          '<div class="style-card" id="styled" style="display:grid!important">Article</div>',
        );
      });
      await page.waitForFunction(
        () => getComputedStyle(document.getElementById("styled")).display === "none",
      );
      await page.evaluate(() => {
        document.body.setAttribute("data-check", "again");
        const rule = document.createElement("style");
        rule.id = "author-style";
        rule.textContent = ".style-card { color: red; }";
        document.head.appendChild(rule);
      });
      await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 150)));
      assert.equal(
        await page.evaluate(() => getComputedStyle(document.getElementById("styled")).display),
        "none",
      );
      await page.evaluate(() =>
        document.getElementById("styled").style.setProperty("display", "flex", "important"),
      );
      await page.waitForFunction(
        () => getComputedStyle(document.getElementById("styled")).display === "flex",
      );
      assert.equal(
        await page.evaluate(() =>
          document.getElementById("styled").style.getPropertyPriority("display"),
        ),
        "important",
      );
    } finally {
      await context.close();
    }
  },
);

test(
  "path predicates respond to history navigation and preserve native history errors",
  options,
  async () => {
    const { context, page } = await cosmeticPage([procedure(".route", [["path", "/news"]])]);
    try {
      await page.evaluate(() => {
        const node = document.createElement("div");
        node.id = "route";
        node.className = "route";
        document.body.appendChild(node);
        history.pushState({}, "", "/news");
      });
      await page.waitForFunction(
        () => getComputedStyle(document.getElementById("route")).display === "none",
      );
      await page.evaluate(() => history.replaceState({}, "", "/article"));
      await page.waitForFunction(
        () => getComputedStyle(document.getElementById("route")).display !== "none",
      );
      assert.equal(
        await page.evaluate(() => {
          try {
            history.pushState({}, "", "https://other.example/");
          } catch (error) {
            return error.name;
          }
        }),
        "SecurityError",
      );
    } finally {
      await context.close();
    }
  },
);

test(
  "Kotlin selector programs agree with the pinned upstream procedural runtime",
  {
    skip:
      options.skip || !process.env.UBLOCK_PROCEDURAL_SOURCE || !process.env.ROADMAP_OUTPUT_DIR,
  },
  async () => {
    const raw = readFileSync(process.env.UBLOCK_PROCEDURAL_SOURCE, "utf8");
    const start = raw.indexOf("const regexFromString");
    const map = raw.indexOf("PSelector.prototype.operatorToTaskMap = new Map(");
    const end = raw.indexOf("\n]);", map) + 4;
    assert.ok(start > 0 && end > map);
    const upstream = raw.slice(start, end) + "\nwindow.UpstreamSelector = PSelector;";
    const programs = JSON.parse(
      readFileSync(process.env.ROADMAP_OUTPUT_DIR + "/procedures.json", "utf8"),
    );
    const escape = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    function nativeProgram(program) {
      return {
        selector: program.selector,
        tasks: program.steps.map((step) => {
          const [name, argument] = step;
          switch (name) {
            case "text":
              return ["has-text", argument];
            case "length":
              return ["min-text-length", Number(argument)];
            case "has":
            case "has-not":
              return [name === "has" ? "has" : "not", nativeProgram(argument)];
            case "css":
              return ["spath", argument];
            case "upward":
              return ["upward", /^\d+$/.test(argument) ? Number(argument) : argument];
            case "attr":
              return ["matches-attr", { attr: argument, value: step[2] }];
            case "style": {
              const match = /^\/(.+)\/([im]*)$/.exec(step[2]);
              return [
                "matches-css",
                {
                  name: argument,
                  pseudo: step[3].replace(/^::/, ""),
                  value: match ? [match[1], match[2]] : "^" + escape(step[2]) + "$",
                },
              ];
            }
            case "path":
              return ["matches-path", argument];
            case "watch":
              return ["watch-attr", argument ? argument.split(",") : []];
            default:
              throw new Error("Unknown procedural test operation: " + name);
          }
        }),
      };
    }
    for (const program of programs) {
      const context = await browser.newContext();
      const page = await context.newPage();
      await page.goto(base + "/news/");
      try {
        await page.evaluate(() => {
          document.body.id = "body";
          document.body.innerHTML =
            '<section id="group">' +
            '<div id="one" class="card" data-kind="advert">Ads Offer ' +
            '<span class="label">Sponsored</span>' +
            '<span id="nested" class="nested">Detail</span></div>' +
            '<div id="two" class="card"><span class="label">News Article</span></div>' +
            '<div id="three" class="card paid">Ads Offer</div></section>' +
            '<div id="route" class="route">Route</div><h2 class="heading">Ads</h2>' +
            '<ul id="list"><li>Offer</li></ul>';
          const css = document.createElement("style");
          css.textContent = '.card { display:block } .card::before { content:"Offer" }';
          document.head.appendChild(css);
          window.originalNodes = Array.from(document.querySelectorAll("[id]"));
        });
        await page.addScriptTag({ content: upstream });
        const expected = await page.evaluate(
          (native) =>
            Array.from(
              new Set(new UpstreamSelector(native).exec(document).map((node) => node.id)),
            )
              .filter(Boolean)
              .sort(),
          nativeProgram(program),
        );
        await page.evaluate(
          (entry) => {
            window.wkCosmeticRules = {
              postMessage(raw) {
                const query = JSON.parse(raw);
                queueMicrotask(() =>
                  this.onmessage({
                    data: JSON.stringify({
                      id: query.id,
                      active: true,
                      selectors: [],
                      procedures: [entry],
                    }),
                  }),
                );
              },
            };
          },
          procedure(program.selector, program.steps),
        );
        await page.addScriptTag({ content: asset("cosmetic.js") });
        await page.waitForFunction((wanted) => {
          const actual = originalNodes
            .filter((node) => node.style.display === "none")
            .map((node) => node.id)
            .sort();
          return JSON.stringify(actual) === JSON.stringify(wanted);
        }, expected);
        // A further mutation also checks that own style writes do not cause feedback.
        await page.evaluate(() => document.body.setAttribute("data-refresh", "yes"));
        await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 60)));
        const actual = await page.evaluate(() =>
          originalNodes
            .filter((node) => node.style.display === "none")
            .map((node) => node.id)
            .sort(),
        );
        assert.deepEqual(actual, expected, JSON.stringify(program));
      } finally {
        await context.close();
      }
    }
  },
);
