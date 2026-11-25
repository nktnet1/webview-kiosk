import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { webcrypto } from 'node:crypto';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const script = readFileSync(new URL('../../app/src/main/assets/native-gps.js', import.meta.url), 'utf8');

function page({ secure = true, mainFrame = true, protocol = 'https:', validate = true } = {}) {
  let now = 0;
  let counter = 0;
  let permissionError;
  const tasks = new Map();
  const messages = [];
  const watches = [];
  const cleared = [];
  const events = new EventTarget();
  const docEvents = new EventTarget();
  const document = { hidden: false, addEventListener: docEvents.addEventListener.bind(docEvents) };
  const geo = {
    getCurrentPosition() { throw new Error('Fused getCurrentPosition must not supply fixes'); },
    watchPosition(success, error, options) {
      permissionError = error;
      watches.push({ success, options });
      return watches.length;
    },
    clearWatch(id) { cleared.push(id); },
  };
  const originalWatch = geo.watchPosition;
  const bridge = { postMessage(data) {
    const message = JSON.parse(data);
    messages.push(message);
    if (message.type === 'validate' && validate) {
      bridge.onmessage({ data: JSON.stringify({
        type: 'validated', id: message.id, token: message.token, allowed: true,
      }) });
    }
  } };
  const context = {
    navigator: { geolocation: geo }, document, location: { protocol },
    WebviewKioskGps: bridge, isSecureContext: secure, crypto: webcrypto,
    Uint32Array, performance: { now: () => now },
    addEventListener: events.addEventListener.bind(events),
    setTimeout(callback, delay = 0) {
      const id = ++counter;
      tasks.set(id, { at: now + delay, callback });
      return id;
    },
    clearTimeout(id) { tasks.delete(id); },
  };
  context.window = context;
  context.top = mainFrame ? context : {};
  runInNewContext(script, context);
  const hello = () => messages.filter(m => m.type === 'hello').at(-1);
  let token = 'document-1';
  function send(data) { bridge.onmessage({ data: JSON.stringify({ token, ...data }) }); }
  function ack(visible = true) {
    send({ type: 'hello', nonce: hello().nonce, visible });
  }
  return {
    geo, messages, watches, cleared, tasks, originalWatch, context, send, ack,
    deny() { permissionError({ code: 1, message: 'Blocked by Permissions Policy' }); },
    grant() { send({ type: 'permission', allowed: true }); },
    fix({ timestamp = 1700000000000, ageMs = 0, latitude = 12.3 } = {}) {
      send({ type: 'position', ageMs, timestamp, coords: {
        latitude, longitude: 45.6, accuracy: 7, altitude: null,
        altitudeAccuracy: null, heading: null, speed: null,
      } });
    },
    advance(ms = 0) {
      const end = now + ms;
      let limit = 1000;
      while (limit--) {
        const entry = [...tasks].filter(([, t]) => t.at <= end).sort((a, b) => a[1].at - b[1].at)[0];
        if (!entry) { now = end; return; }
        tasks.delete(entry[0]);
        now = entry[1].at;
        entry[1].callback();
      }
      throw new Error('Timer loop');
    },
    visibility(hidden) {
      document.hidden = hidden;
      docEvents.dispatchEvent(new Event('visibilitychange'));
    },
    hide() { events.dispatchEvent(new Event('pagehide')); },
    restore() {
      token = 'document-2';
      const event = new Event('pageshow');
      Object.defineProperty(event, 'persisted', { value: true });
      events.dispatchEvent(event);
    },
  };
}

test('only secure HTTPS main frames override the browser API', () => {
  for (const options of [{ secure: false }, { mainFrame: false }, { protocol: 'http:' }]) {
    const p = page(options);
    assert.equal(p.geo.watchPosition, p.originalWatch);
    assert.equal(p.messages.length, 0);
  }
});

test('a dropped hello is retried and a later reply starts the pending request', () => {
  const p = page();
  const first = p.messages[0];
  let fix;
  p.geo.getCurrentPosition(position => { fix = position; });
  p.advance(250);
  assert.equal(p.messages.filter(message => message.type === 'hello').length, 2);
  assert.equal(p.messages.at(-1).nonce, first.nonce);
  p.ack();
  p.grant();
  p.fix();
  p.advance(6000);
  assert.equal(fix.coords.latitude, 12.3);
  assert.equal(p.messages.filter(message => message.type === 'hello').length, 2);
  assert.equal(p.tasks.size, 0);
});

test('a missing reply or failed transport returns position unavailable within the deadline', () => {
  const p = page();
  const errors = [];
  const oldNonce = p.messages[0].nonce;
  p.geo.getCurrentPosition(() => assert.fail('unexpected position'), e => errors.push(e), { timeout: 30000 });
  p.advance(3000);
  assert.deepEqual(errors.map(error => error.code), [2]);
  assert.equal(p.watches.length, 0);
  assert.equal(p.tasks.size, 0);
  p.context.WebviewKioskGps.postMessage = () => { throw new Error('WebView detached'); };
  p.send({ type: 'hello', nonce: oldNonce, visible: true });
  let secondError;
  p.geo.getCurrentPosition(() => assert.fail('unexpected position'), error => { secondError = error; });
  p.advance(3000);
  assert.equal(secondError.code, 2);
  assert.equal(p.tasks.size, 0);
});

test('a new request reconnects after the previous handshake timed out', () => {
  const p = page();
  const first = p.messages[0];
  p.advance(3000);
  let calls = 0;
  p.geo.getCurrentPosition(() => calls++);
  assert.notEqual(p.messages.at(-1).nonce, first.nonce);
  p.send({ type: 'hello', nonce: first.nonce, visible: true });
  assert.equal(p.watches.length, 0);
  p.ack();
  p.grant();
  p.fix();
  p.advance();
  assert.equal(calls, 1);
});

test('a missing validation reply ends the request and releases the session', () => {
  const p = page({ validate: false });
  p.ack();
  let error;
  p.geo.watchPosition(() => assert.fail('unvalidated fix'), e => { error = e; });
  p.grant();
  p.advance(3000);
  assert.equal(error.code, 2);
  assert.equal(p.messages.at(-1).type, 'release');
  assert.equal(p.messages.some(message => message.type === 'subscribe'), false);
  assert.equal(p.tasks.size, 0);
});

test('a finished browser probe cannot grant GPS and a missing permission reply returns an error', () => {
  const p = page();
  p.ack();
  let error;
  p.geo.watchPosition(() => assert.fail('unapproved fix'), e => { error = e; });
  p.advance(10000);
  assert.equal(error, undefined, 'An open permission prompt is not a bridge failure');
  p.watches[0].success();
  p.advance(3000);
  assert.equal(error.code, 2);
  assert.equal(p.messages.some(message => message.type === 'subscribe'), false);
  assert.deepEqual(p.cleared, [1]);
});

test('native subscription waits for the genuine browser permission callback, not a fused fix', () => {
  const p = page();
  p.ack();
  p.geo.getCurrentPosition(() => {});
  assert.equal(p.watches.length, 1);
  assert.equal(p.messages.filter(m => m.type === 'subscribe').length, 0);
  p.grant();
  assert.deepEqual(p.cleared, [1]);
  assert.equal(p.messages.at(-1).type, 'subscribe');
});

test('browser permission or policy denial terminates requests without subscribing', () => {
  const p = page();
  p.ack();
  let error;
  p.geo.watchPosition(() => assert.fail('denied'), e => { error = e; });
  p.deny();
  p.advance();
  assert.equal(error.code, 1);
  assert.equal(error.PERMISSION_DENIED, 1);
  assert.match(error.message, /Policy/);
  assert.equal(p.messages.some(m => m.type === 'subscribe'), false);
});

test('app permission denial and approximate-only native errors reach callers', () => {
  for (const native of [false, true]) {
    const p = page();
    p.ack();
    let error;
    p.geo.getCurrentPosition(() => assert.fail('denied'), e => { error = e; });
    if (native) {
      p.grant();
      p.send({ type: 'error', code: 1, message: 'Precise location required' });
    } else {
      p.send({ type: 'permission', allowed: false });
    }
    p.advance();
    assert.equal(error.code, 1);
    assert.equal(p.tasks.size, 0);
  }
});

test('one fix serves a one-shot and multiple watches with one native subscription', () => {
  const p = page();
  p.ack();
  const values = [];
  assert.equal(p.geo.getCurrentPosition(v => values.push(v)), undefined);
  const a = p.geo.watchPosition(v => values.push(v));
  const b = p.geo.watchPosition(v => values.push(v));
  assert.ok(a > 0 && b > a);
  p.grant();
  p.fix();
  assert.equal(values.length, 0, 'Callbacks must be asynchronous');
  p.advance();
  assert.equal(values.length, 3);
  assert.equal(values[0].coords.accuracy, 7);
  assert.equal(values[0].coords.altitude, null);
  assert.deepEqual(JSON.parse(JSON.stringify(values[0])), {
    coords: { latitude: 12.3, longitude: 45.6, accuracy: 7, altitude: null,
      altitudeAccuracy: null, heading: null, speed: null },
    timestamp: 1700000000000,
  });
  p.geo.clearWatch(a);
  assert.equal(p.messages.at(-1).type, 'subscribe');
  p.fix({ latitude: 13 });
  p.advance();
  assert.equal(values.length, 4);
  p.geo.clearWatch(b);
  assert.equal(p.messages.at(-1).type, 'unsubscribe');
});

test('clearWatch suppresses queued callbacks and cancels a pending permission probe', () => {
  const p = page();
  p.ack();
  const id = p.geo.watchPosition(() => assert.fail('cleared watch'));
  p.geo.clearWatch(id);
  assert.deepEqual(p.cleared, [1]);
  p.grant();
  const next = p.geo.watchPosition(() => assert.fail('queued callback'));
  p.fix();
  p.geo.clearWatch(next);
  p.advance();
  assert.equal(p.messages.at(-1).type, 'unsubscribe');
});

test('one-shot unsubscribes immediately after its fresh fix', () => {
  const p = page();
  p.ack();
  let calls = 0;
  p.geo.getCurrentPosition(() => calls++);
  p.grant();
  p.fix();
  assert.equal(p.messages.at(-1).type, 'unsubscribe');
  p.fix();
  p.advance();
  assert.equal(calls, 1);
});

test('one-shot times out and a timed-out watch can recover with a later fix', () => {
  const p = page();
  p.ack();
  const errors = [];
  let fixes = 0;
  p.geo.getCurrentPosition(() => assert.fail('expired'), e => errors.push(e), { timeout: 100 });
  const id = p.geo.watchPosition(() => fixes++, e => errors.push(e), { timeout: 200 });
  p.grant();
  p.advance(199);
  assert.equal(errors.length, 1);
  p.advance(1);
  assert.equal(errors.length, 2);
  assert.equal(errors[0].code, 3);
  p.advance(1000);
  assert.equal(errors.length, 2, 'A watch must not spin after timing out');
  p.fix();
  p.advance();
  assert.equal(fixes, 1);
  p.geo.clearWatch(id);
});

test('permission and hidden time do not consume the acquisition timeout', () => {
  const p = page();
  p.ack();
  let errors = 0;
  p.geo.getCurrentPosition(() => {}, () => errors++, { timeout: 100 });
  p.advance(10000);
  assert.equal(errors, 0);
  p.grant();
  p.advance(40);
  p.visibility(true);
  p.advance(10000);
  assert.equal(errors, 0);
  assert.equal(p.messages.at(-1).type, 'unsubscribe');
  p.visibility(false);
  p.advance(59);
  assert.equal(errors, 0);
  p.advance(1);
  assert.equal(errors, 1);
});

test('native host sleep suspends even if the page stays visible, and wake resubscribes', () => {
  const p = page();
  p.ack();
  let calls = 0;
  p.geo.watchPosition(() => calls++);
  p.grant();
  p.send({ type: 'foreground', visible: false });
  p.fix();
  p.advance();
  assert.equal(calls, 0);
  assert.equal(p.messages.at(-1).type, 'unsubscribe');
  p.send({ type: 'foreground', visible: true });
  assert.equal(p.messages.at(-1).type, 'subscribe');
  p.fix();
  p.advance();
  assert.equal(calls, 1);
});

test('maximumAge uses monotonic fix age and the default always waits for a new fix', () => {
  const p = page();
  p.ack();
  p.geo.getCurrentPosition(() => {});
  p.grant();
  p.fix({ ageMs: 10 });
  p.advance(20);
  let cached = 0;
  p.geo.getCurrentPosition(() => cached++, null, { maximumAge: 31 });
  p.advance();
  assert.equal(cached, 1);
  let fresh = 0;
  p.geo.getCurrentPosition(() => fresh++);
  p.geo.getCurrentPosition(() => fresh++, null, { maximumAge: 29 });
  p.advance();
  assert.equal(fresh, 0);
  p.fix();
  p.advance();
  assert.equal(fresh, 2);
});

test('zero timeout reports asynchronously and leaves no one-shot subscription', () => {
  const p = page();
  p.ack();
  let error;
  p.geo.getCurrentPosition(() => {}, e => { error = e; }, { timeout: 0 });
  p.grant();
  assert.equal(error, undefined);
  p.advance();
  assert.equal(error.code, 3);
  assert.equal(p.messages.at(-1).type, 'unsubscribe');
});

test('a provider error terminates one-shots but an active watch can recover', () => {
  const p = page();
  p.ack();
  const errors = [];
  let fixes = 0;
  p.geo.getCurrentPosition(() => assert.fail('failed'), e => errors.push(e));
  p.geo.watchPosition(() => fixes++, e => errors.push(e));
  p.grant();
  p.send({ type: 'error', code: 2, message: 'GPS disabled' });
  p.advance();
  assert.equal(errors.length, 2);
  assert.equal(errors[0].code, 2);
  p.fix();
  p.advance();
  assert.equal(fixes, 1);
});

test('stale documents cannot supply permission, positions or host state', () => {
  const p = page();
  p.ack();
  let calls = 0;
  p.geo.watchPosition(() => calls++);
  p.send({ type: 'permission', allowed: true, token: 'old' });
  assert.equal(p.messages.some(m => m.type === 'subscribe'), false);
  p.grant();
  p.send({ type: 'position', token: 'old', coords: {} });
  p.send({ type: 'foreground', token: 'old', visible: false });
  p.fix();
  p.advance();
  assert.equal(calls, 1);
});

test('BFCache restore reauthorizes the new native session and preserves active watches', () => {
  const p = page();
  p.ack();
  let calls = 0;
  p.geo.watchPosition(() => calls++);
  p.grant();
  p.hide();
  assert.equal(p.messages.at(-1).type, 'release');
  p.fix();
  p.advance();
  assert.equal(calls, 0);
  p.restore();
  p.ack();
  assert.equal(p.watches.length, 2);
  p.grant();
  p.fix();
  p.advance();
  assert.equal(calls, 1);
});

test('large finite timeouts are split without a signed timer overflow', () => {
  const p = page();
  p.ack();
  let errors = 0;
  p.geo.getCurrentPosition(() => {}, () => errors++, { timeout: 0x80000002 });
  p.grant();
  p.advance(0x7fffffff);
  assert.equal(errors, 0);
  p.advance(3);
  assert.equal(errors, 1);
});

test('callback and dictionary validation happen synchronously before a native request', () => {
  const p = page();
  p.ack();
  assert.throws(() => p.geo.getCurrentPosition(null), { name: 'TypeError' });
  assert.throws(() => p.geo.watchPosition(() => {}, 5), { name: 'TypeError' });
  assert.throws(() => p.geo.watchPosition(() => {}, null, 5), { name: 'TypeError' });
  assert.throws(() => p.geo.clearWatch(), { name: 'TypeError' });
  assert.equal(p.watches.length, 0);
});

test('each request validates current Android and app permissions before exposing a cached fix', () => {
  const p = page({ validate: false });
  p.ack();
  p.geo.getCurrentPosition(() => {});
  p.grant();
  let validation = p.messages.at(-1);
  assert.equal(validation.type, 'validate');
  p.send({ type: 'validated', id: validation.id, allowed: true });
  p.fix();
  p.advance();
  let error;
  p.geo.getCurrentPosition(() => assert.fail('Revoked permission must block cached coordinates'), e => {
    error = e;
  }, { maximumAge: 10000 });
  validation = p.messages.at(-1);
  assert.equal(validation.type, 'validate');
  p.send({ type: 'validated', id: validation.id, allowed: false });
  p.advance();
  assert.equal(error.code, 1);
});

test('pending validation cannot receive another watcher fix', () => {
  const p = page({ validate: false });
  p.ack();
  let approved = 0;
  let pending = 0;
  const id = p.geo.watchPosition(() => approved++);
  p.grant();
  p.send({ type: 'validated', id, allowed: true });
  p.geo.getCurrentPosition(() => pending++);
  const validation = p.messages.at(-1);
  p.fix();
  p.advance();
  assert.equal(approved, 1);
  assert.equal(pending, 0);
  p.send({ type: 'validated', id: validation.id, allowed: true });
  p.fix();
  p.advance();
  assert.equal(pending, 1);
});

test('position properties are read-only so one callback cannot corrupt another or the cache', () => {
  const p = page();
  p.ack();
  let position;
  p.geo.getCurrentPosition(v => { position = v; });
  p.grant();
  p.fix();
  p.advance();
  assert.throws(() => { position.coords.latitude = 99; }, TypeError);
  assert.throws(() => { position.timestamp = 0; }, TypeError);
});

test('a queued permission error from the cancelled browser probe cannot terminate native GPS', () => {
  const p = page();
  p.ack();
  let calls = 0;
  p.geo.watchPosition(() => calls++, () => assert.fail('Stale browser error'));
  p.grant();
  p.deny();
  p.fix();
  p.advance();
  assert.equal(calls, 1);
});

test('sleep cancels a pending browser probe and wake requests permission again', () => {
  const p = page();
  p.ack();
  p.geo.getCurrentPosition(() => {});
  p.send({ type: 'foreground', visible: false });
  assert.deepEqual(p.cleared, [1]);
  p.send({ type: 'foreground', visible: true });
  assert.equal(p.watches.length, 2);
});

test('callbacks do not expose private request state through their receiver', () => {
  const p = page();
  p.ack();
  let receiver = 'unset';
  p.geo.getCurrentPosition(function () { receiver = this; });
  p.grant();
  p.fix();
  p.advance();
  assert.equal(receiver, undefined);
});

test('a queued one-shot fix waits for visibility and a fresh fix after wake', () => {
  const p = page();
  p.ack();
  let calls = 0;
  p.geo.getCurrentPosition(() => calls++);
  p.grant();
  p.fix();
  p.send({ type: 'foreground', visible: false });
  p.advance();
  assert.equal(calls, 0);
  p.send({ type: 'foreground', visible: true });
  assert.equal(p.messages.at(-1).type, 'subscribe');
  p.fix();
  p.advance();
  assert.equal(calls, 1);
});

test('a watch and one-shot both discard pre-sleep fixes when wake precedes callback delivery', () => {
  for (const watch of [false, true]) {
    for (const changeVisibility of [false, true]) {
      const p = page();
      p.ack();
      const fixes = [];
      const method = watch ? 'watchPosition' : 'getCurrentPosition';
      p.geo[method](fix => fixes.push(fix.timestamp));
      p.grant();
      p.fix({ timestamp: 100 });
      if (changeVisibility) {
        p.visibility(true);
        p.visibility(false);
      } else {
        p.send({ type: 'foreground', visible: false });
        p.send({ type: 'foreground', visible: true });
      }
      p.fix({ timestamp: 200 });
      p.advance();
      assert.deepEqual(fixes, [200]);
    }
  }
});

test('permission revocation cancels an already queued position callback', () => {
  const p = page();
  p.ack();
  let error;
  p.geo.getCurrentPosition(() => assert.fail('Revoked queued fix'), e => { error = e; });
  p.grant();
  p.fix();
  p.send({ type: 'error', code: 1, message: 'Revoked' });
  p.advance();
  assert.equal(error.code, 1);
});

test('sleep clears the JavaScript fix cache even if its monotonic clock barely advances', () => {
  const p = page();
  p.ack();
  p.geo.getCurrentPosition(() => {});
  p.grant();
  p.fix();
  p.advance();
  p.send({ type: 'foreground', visible: false });
  p.send({ type: 'foreground', visible: true });
  let calls = 0;
  p.geo.getCurrentPosition(() => calls++, null, { maximumAge: 100000 });
  p.advance();
  assert.equal(calls, 0);
  p.fix();
  p.advance();
  assert.equal(calls, 1);
});
