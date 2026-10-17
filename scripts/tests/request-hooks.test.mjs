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
    "https://frame.example/frame",
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
    "https://frame.example/frame",
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
