import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { runInNewContext } from "node:vm";

// Execute the actual JavaScript shipped inside NfcInterface, not a test copy.
const kotlinSource = readFileSync(
  new URL(
    "../../app/src/main/java/uk/nktnet/webviewkiosk/utils/webview/interfaces/NfcInterface.kt",
    import.meta.url,
  ),
  "utf8",
);
const hook = kotlinSource.match(/const val JS_WEB_NFC_HOOK\s*=\s*"""([\s\S]*?)"""/);
assert.ok(hook, "Web NFC injection script must exist");

function createNativeBridge() {
  return {
    enabled: true,
    scanCalls: [],
    stopCalls: 0,
    resetCalls: 0,
    clientCounter: 0,
    clients: new Set(),
    scanningClients: new Set(),
    openClient() {
      const id = `nfc-client-${++this.clientCounter}`;
      this.clients.add(id);
      return id;
    },
    scanForClient(id, options) {
      this.scanCalls.push(options);
      if (!this.enabled || !this.clients.has(id)) return false;
      this.scanningClients.add(id);
      return true;
    },
    stopScanForClient(id) {
      if (!this.clients.has(id)) return;
      if (this.scanningClients.delete(id) && this.scanningClients.size === 0) {
        this.stopCalls++;
      }
    },
    releaseClient(id) {
      this.stopScanForClient(id);
      this.clients.delete(id);
    },
    writeForClient(id) {
      return JSON.stringify(this.clients.has(id)
        ? { ok: true, requestId: 'test-write' }
        : { ok: false, errorName: 'InvalidStateError' });
    },
    reset() {
      this.resetCalls++;
      this.clients.clear();
      this.scanningClients.clear();
    },
  };
}

function page(nativeBridge = createNativeBridge()) {
  const windowEvents = new EventTarget();
  const documentEvents = new EventTarget();
  const document = {
    hidden: false,
    addEventListener: documentEvents.addEventListener.bind(documentEvents),
  };
  const context = {
    AbortController,
    ArrayBuffer,
    DOMException,
    Event,
    EventTarget,
    TextDecoder,
    TextEncoder,
    Uint8Array,
    WebviewKioskNfcInterface: nativeBridge,
    addEventListener: windowEvents.addEventListener.bind(windowEvents),
    document,
    navigator: {},
  };
  context.window = context;
  return {
    nativeBridge,
    context,
    install() {
      runInNewContext(hook[1], context);
    },
    eval(expression) {
      return runInNewContext(expression, context);
    },
    fire(type, { persisted = false } = {}) {
      const event = new Event(type);
      if (persisted) Object.defineProperty(event, "persisted", { value: true });
      windowEvents.dispatchEvent(event);
    },
    visibilityChange(hidden) {
      document.hidden = hidden;
      documentEvents.dispatchEvent(new Event("visibilitychange"));
    },
  };
}

test("stopping one reader preserves other active readers and native scanning", async () => {
  const p = page();
  p.install();
  const first = p.eval("new NDEFReader()");
  const second = p.eval("new NDEFReader()");
  await first.scan();
  await second.scan();
  let firstReads = 0;
  let secondReads = 0;
  first.addEventListener("reading", () => firstReads++);
  second.addEventListener("reading", () => secondReads++);

  first.stopScan();
  first.stopScan();
  assert.equal(p.nativeBridge.stopCalls, 0);
  p.context.__WebviewKioskNfcBridge.onTagScanned({ records: [] });
  assert.equal(firstReads, 0);
  assert.equal(secondReads, 1);

  second.stopScan();
  second.stopScan();
  assert.equal(p.nativeBridge.stopCalls, 1);
});

test("repeated scans of the same reader never create duplicate callbacks", async () => {
  const p = page();
  p.install();
  p.install(); // onPageFinished fallback must be safe after document-start injection
  const reader = p.eval("new NDEFReader()");
  await reader.scan();
  await reader.scan();
  let readCount = 0;
  reader.onreading = () => readCount++;
  p.context.__WebviewKioskNfcBridge.onTagScanned({ records: [] });
  assert.equal(readCount, 1);
  reader.stopScan();
  assert.equal(p.nativeBridge.stopCalls, 1);
});

test("late availability of the native interface permits a second injection", async () => {
  const p = page();
  const nativeBridge = p.nativeBridge;
  delete p.context.WebviewKioskNfcInterface;
  p.install();
  assert.equal(p.context.__webviewKioskWebNfcInstalled, undefined);

  p.context.WebviewKioskNfcInterface = nativeBridge;
  p.install();
  await p.eval("new NDEFReader()").scan();
  assert.equal(nativeBridge.scanCalls.length, 1);
});

test("abort removes only the matching reader and rejects a pre-aborted scan", async () => {
  const p = page();
  p.install();
  const first = p.eval("new NDEFReader()");
  const second = p.eval("new NDEFReader()");
  const firstController = new AbortController();
  const secondController = new AbortController();
  await first.scan({ signal: firstController.signal });
  await second.scan({ signal: secondController.signal });
  firstController.abort();
  assert.equal(p.nativeBridge.stopCalls, 0);

  const alreadyAborted = new AbortController();
  alreadyAborted.abort();
  const scansBefore = p.nativeBridge.scanCalls.length;
  await assert.rejects(first.scan({ signal: alreadyAborted.signal }), {
    name: "AbortError",
  });
  assert.equal(p.nativeBridge.scanCalls.length, scansBefore);

  secondController.abort();
  assert.equal(p.nativeBridge.stopCalls, 1);
});

test("replacing a reader's abort signal detaches its previous listener", async () => {
  const p = page();
  p.install();
  const reader = p.eval("new NDEFReader()");
  const oldController = new AbortController();
  const newController = new AbortController();
  await reader.scan({ signal: oldController.signal });
  await reader.scan({ signal: newController.signal });
  oldController.abort();
  assert.equal(p.nativeBridge.stopCalls, 0);
  newController.abort();
  assert.equal(p.nativeBridge.stopCalls, 1);
});

test("wake and page visibility re-arm existing readers without creating duplicates", async () => {
  const p = page();
  p.install();
  p.fire("pageshow");
  assert.equal(p.nativeBridge.scanCalls.length, 0);

  const reader = p.eval("new NDEFReader()");
  await reader.scan();
  p.fire("pageshow");
  p.fire("focus");
  p.visibilityChange(true);
  assert.equal(p.nativeBridge.scanCalls.length, 3);
  p.visibilityChange(false);
  assert.equal(p.nativeBridge.scanCalls.length, 4);

  let reads = 0;
  reader.addEventListener("reading", () => reads++);
  p.context.__WebviewKioskNfcBridge.onTagScanned({ records: [] });
  assert.equal(reads, 1);
  reader.stopScan();
  p.fire("focus");
  assert.equal(p.nativeBridge.scanCalls.length, 4);
});

test("failed re-arm retains readers for recovery when native NFC becomes available", async () => {
  const p = page();
  p.install();
  const reader = p.eval("new NDEFReader()");
  await reader.scan();
  p.nativeBridge.enabled = false;
  p.fire("focus");
  assert.equal(p.nativeBridge.scanCalls.length, 2);

  p.nativeBridge.enabled = true;
  p.context.__WebviewKioskNfcBridge.rearmScan();
  assert.equal(p.nativeBridge.scanCalls.length, 3);
  let reads = 0;
  reader.addEventListener("reading", () => reads++);
  p.context.__WebviewKioskNfcBridge.onTagScanned({ records: [] });
  assert.equal(reads, 1);
});

test("old-page unload never resets the new page's native scan", async () => {
  const nativeBridge = createNativeBridge();
  const oldPage = page(nativeBridge);
  oldPage.install();
  await oldPage.eval("new NDEFReader()").scan();
  oldPage.fire("beforeunload");
  assert.equal(nativeBridge.resetCalls, 0);

  // Android WebViewClient.onPageStarted owns the native reset. The new
  // JavaScript context installs its own hook and starts its own scan.
  nativeBridge.reset();
  const newPage = page(nativeBridge);
  newPage.install();
  newPage.fire("pageshow");
  assert.equal(nativeBridge.scanCalls.length, 1);
  await newPage.eval("new NDEFReader()").scan();
  assert.equal(nativeBridge.scanCalls.length, 2);
  oldPage.fire("beforeunload");
  assert.equal(nativeBridge.resetCalls, 1);
});

test("a stale page cannot stop or restart a new page's scan", async () => {
  const bridge = createNativeBridge();
  const oldPage = page(bridge);
  oldPage.install();
  const oldReader = oldPage.eval("new NDEFReader()");
  await oldReader.scan();

  bridge.reset();
  const newPage = page(bridge);
  newPage.install();
  const newReader = newPage.eval("new NDEFReader()");
  await newReader.scan();

  const scans = bridge.scanCalls.length;
  oldPage.fire("focus"); // stale re-arm must not become an active scanner
  assert.equal(bridge.scanCalls.length, scans + 1);
  assert.equal(bridge.scanningClients.size, 1);

  oldReader.stopScan();
  oldPage.fire("pagehide");
  assert.equal(bridge.stopCalls, 0);
  assert.equal(bridge.scanningClients.size, 1);
  await assert.rejects(oldReader.write("stale write"), {
    name: "InvalidStateError",
  });
  let reads = 0;
  newReader.onreading = () => reads++;
  newPage.context.__WebviewKioskNfcBridge.onTagScanned({ records: [] });
  assert.equal(reads, 1);
  newReader.stopScan();
  assert.equal(bridge.stopCalls, 1);
});

test("different page contexts cannot stop each other's native scan", async () => {
  const bridge = createNativeBridge();
  const first = page(bridge);
  const second = page(bridge);
  first.install();
  second.install();
  const firstReader = first.eval("new NDEFReader()");
  const secondReader = second.eval("new NDEFReader()");
  await firstReader.scan();
  await secondReader.scan();

  firstReader.stopScan();
  assert.equal(bridge.stopCalls, 0);
  assert.equal(bridge.scanningClients.size, 1);
  first.fire("pagehide");
  assert.equal(bridge.clients.size, 1);
  secondReader.stopScan();
  assert.equal(bridge.stopCalls, 1);
});

test("pagehide releases a client but bfcache restores its readers", async () => {
  const bridge = createNativeBridge();
  const p = page(bridge);
  p.install();
  const reader = p.eval("new NDEFReader()");
  await reader.scan();

  p.fire("pagehide", { persisted: true });
  assert.equal(bridge.clients.size, 1);
  assert.equal(bridge.scanningClients.size, 0);
  p.fire("pageshow");
  assert.equal(bridge.scanningClients.size, 1);
  reader.stopScan();
  assert.equal(bridge.scanningClients.size, 0);
});
