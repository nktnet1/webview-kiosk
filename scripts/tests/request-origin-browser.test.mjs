import assert from "node:assert/strict";
import { createServer } from "node:http";
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import { after, before, test } from "node:test";

const options = { skip: !process.env.SCRIPTLET_CHROMIUM };
const source = readFileSync(
  new URL("../../app/src/main/assets/content-blocking/requests.js", import.meta.url),
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
  server = createServer((_, res) => {
    res.writeHead(200, { "Content-Type": "text/html" });
    res.end("<!doctype html><body>Frame context</body>");
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  base = "http://127.0.0.1:" + server.address().port;
});
after(async () => {
  await browser?.close();
  if (server) await new Promise((resolve) => server.close(resolve));
});

async function withPage(run) {
  const context = await browser.newContext();
  try {
    await context.addInitScript({
      content: `
        window.policyCalls = [];
        window.wkRequestRules = { shouldBlock(...args) {
          policyCalls.push(args); return args[0].includes('/blocked');
        } };
        ${source}
      `,
    });
    const page = await context.newPage();
    await page.goto(base);
    await run(page);
  } finally {
    await context.close();
  }
}

async function addFrame(page, name, source, sandbox) {
  await page.evaluate(
    ({ name, source, sandbox }) =>
      new Promise((resolve) => {
        const frame = document.createElement("iframe");
        frame.name = name;
        if (sandbox) frame.sandbox = sandbox;
        frame.onload = resolve;
        if (source === "srcdoc") frame.srcdoc = "<!doctype html><p>Frame</p>";
        else frame.src = source;
        document.body.appendChild(frame);
      }),
    { name, source, sandbox },
  );
  return page.frames().find((frame) => frame.name() === name);
}

async function query(frame) {
  return frame.evaluate(() => {
    const actualOrigin = origin;
    window.origin = "https://replaced.example";
    let socketError;
    try {
      new WebSocket("/blocked");
    } catch (error) {
      socketError = error.name;
    }
    return {
      actualOrigin,
      socketError,
      beacon: navigator.sendBeacon("/blocked", "data"),
      sources: policyCalls.map((args) => args[1]),
    };
  });
}

test("blank and srcdoc request hooks use the inherited frame origin", options, async () => {
  await withPage(async (page) => {
    for (const source of ["about:blank", "srcdoc"]) {
      const frame = await addFrame(page, source, source);
      const result = await query(frame);
      assert.equal(result.actualOrigin, base);
      assert.equal(result.socketError, "SecurityError");
      assert.equal(result.beacon, true);
      assert.deepEqual(result.sources, [base, base]);
    }
  });
});

test("sandboxed frames keep unknown initiators even with HTTP document URLs", options, async () => {
  await withPage(async (page) => {
    for (const source of [base + "/frame", "srcdoc"]) {
      const frame = await addFrame(page, source, source, "allow-scripts");
      const result = await query(frame);
      assert.equal(result.actualOrigin, "null");
      assert.equal(result.socketError, "SecurityError");
      assert.equal(result.beacon, true);
      assert.deepEqual(result.sources, ["", ""]);
    }
  });
});
