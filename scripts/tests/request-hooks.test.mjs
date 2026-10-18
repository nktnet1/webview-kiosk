import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { test } from "node:test";

const source = readFileSync(
  new URL("../../app/src/main/assets/content-blocking/requests.js", import.meta.url),
  "utf8",
);
function page(host = "frame.example", policy = (url) => url.includes("blocked")) {
  const queries = [],
    sockets = [],
    beacons = [];
  class Socket {
    static OPEN = 1;
    constructor(...args) {
      this.args = args;
      sockets.push(this);
    }
    send() {
      return "native";
    }
  }
  const navigator = {
    sendBeacon(...args) {
      if (this !== navigator) throw new TypeError("Receiver");
      beacons.push(args);
      return false;
    },
  };
  const context = {
    URL,
    DOMException,
    WebSocket: Socket,
    navigator,
    origin: "https://" + host,
    location: { protocol: "https:", hostname: host, href: "https://" + host + "/frame" },
    document: { baseURI: "https://api.example/base/" },
    wkRequestRules: {
      shouldBlock(...args) {
        queries.push(args);
        return policy(...args);
      },
    },
  };
  context.window = context;
  return {
    context,
    queries,
    sockets,
    beacons,
    Socket,
    run(code) {
      return runInNewContext(code, context);
    },
    install() {
      runInNewContext(source, context);
    },
  };
}

test("WebSocket hooks retain native instances and suppress blocked connections", () => {
  const p = page();
  p.install();
  p.install();
  assert.throws(() => p.run('new WebSocket("/blocked")'), { name: "SecurityError" });
  assert.equal(p.sockets.length, 0);
  const socket = p.run('new WebSocket("wss://safe.example/socket", ["chat"])');
  assert.ok(socket instanceof p.Socket);
  assert.equal(socket.send(), "native");
  assert.equal(p.context.WebSocket.OPEN, p.Socket.OPEN);
  assert.equal(p.sockets.length, 1);
  assert.deepEqual(p.queries[0], [
    "wss://api.example/blocked",
    "https://frame.example",
    "websocket",
  ]);
  assert.throws(() => p.run('WebSocket("/safe")'), { name: "TypeError" });
});

test("beacon hooks preserve return values, receivers, and suppression", () => {
  const p = page();
  p.install();
  assert.equal(p.run('navigator.sendBeacon("/blocked", "payload")'), true);
  assert.equal(p.beacons.length, 0);
  assert.equal(p.run('navigator.sendBeacon("/safe", "payload")'), false);
  assert.equal(p.beacons.length, 1);
  assert.throws(() => p.run('navigator.sendBeacon.call({}, "/blocked")'), {
    name: "TypeError",
  });
  assert.deepEqual(p.queries[0], [
    "https://api.example/blocked",
    "https://frame.example",
    "ping",
  ]);
});

test("internal documents and failed policy lookups retain native APIs", () => {
  const internal = page("appassets.androidplatform.net");
  internal.install();
  assert.equal(internal.context.WebSocket, internal.Socket);
  assert.equal(internal.queries.length, 0);
  const unavailable = page("frame.example", () => {
    throw new Error("Unavailable");
  });
  unavailable.install();
  unavailable.run('new WebSocket("/blocked")');
  assert.equal(unavailable.sockets.length, 1);
});

test("inherited frame origins are captured without borrowing the document URL", () => {
  for (const href of ["about:blank", "about:srcdoc", "blob:https://other.example/id"]) {
    const p = page();
    p.context.location = { protocol: href.split(":")[0] + ":", hostname: "", href };
    p.install();
    p.context.origin = "https://replaced.example";
    assert.equal(p.run('navigator.sendBeacon("/blocked")'), true);
    assert.equal(p.queries[0][1], "https://frame.example");
  }
});

test("opaque frame origins query only context-free policy", () => {
  for (const origin of ["null", undefined, "malformed"]) {
    const p = page();
    p.context.origin = origin;
    p.install();
    assert.equal(p.run('navigator.sendBeacon("/blocked")'), true);
    assert.equal(p.queries[0][1], "");
  }
});

test("local documents and inherited private origins keep native APIs", () => {
  for (const protocol of ["file:", "content:"]) {
    const p = page();
    p.context.location.protocol = protocol;
    p.context.origin = "null";
    p.install();
    assert.equal(p.context.WebSocket, p.Socket);
    assert.equal(p.queries.length, 0);
  }
  const p = page();
  p.context.location.href = "about:blank";
  p.context.location.protocol = "about:";
  p.context.origin = "https://appassets.androidplatform.net";
  p.install();
  assert.equal(p.context.WebSocket, p.Socket);
});
