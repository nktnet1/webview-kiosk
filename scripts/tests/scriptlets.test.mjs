import assert from 'node:assert/strict';
import test from 'node:test';
import { page, installed, runtime } from './scriptlet-fixture.mjs';

test('set hooks nested missing properties, replacement parents, and predefined functions', () => {
    const p = page([
        ['SET', ['player.ads', 'undefined']],
        ['SET', ['player.enabled', 'false']],
        ['SET', ['adCallback', 'trueFunc']],
    ]);
    p.install();
    assert.equal(p.run('player'), undefined);
    assert.equal(p.run('player = { ads: [1], enabled: true }; player.ads'), undefined);
    assert.equal(p.run('player.enabled'), false);
    assert.equal(p.run('player = {}; player.enabled = true; player.enabled'), false);
    assert.equal(p.run('adCallback()'), true);
});

test('set releases on type changes, preserves first conflicting constant', () => {
    const p = page([['SET', ['enabled', 'false']], ['SET', ['enabled', 'true']]]);
    p.install();
    assert.equal(p.run('enabled'), false);
    assert.equal(p.run('enabled = true; enabled'), false);
    assert.equal(p.run('enabled = "login data"; enabled'), 'login data');
    assert.equal(p.run('enabled = 1; enabled'), 1);
});

test('set refuses an incompatible existing value', () => {
    const p = installed('SET', ['session', 'false'], { before: 'session = { token: "ok" };' });
    assert.equal(p.run('session.token'), 'ok');
});

test('aopr aborts reads and ignores writes on nested and reassigned owners', () => {
    const p = installed('ABORT_READ', ['ads.detect']);
    p.run('ads = {}; ads.detect = 5');
    assert.throws(() => p.run('ads.detect'), { name: 'ReferenceError' });
    p.run('ads = {}');
    assert.throws(() => p.run('ads.detect'), { name: 'ReferenceError' });
});

test('aopw aborts writes; unrelated login and authentication properties survive', () => {
    const p = installed('ABORT_WRITE', ['adMarker']);
    assert.throws(() => p.run('adMarker = true'), { name: 'ReferenceError' });
    assert.equal(p.run('adMarker'), undefined);
    assert.equal(p.run('session = { token: "ok" }; session.token'), 'ok');
});

test('read-only and non-configurable properties do not interrupt other rules', () => {
    const p = page([
        ['ABORT_READ', ['fixed']], ['SET', ['other', 'false']],
        ['SET', ['fixedObject.ads', 'false']],
    ], { before: 'Object.defineProperty(self, "fixed", { value: 7 });' +
        'Object.defineProperty(self, "fixedObject", { value: {} });' });
    p.install();
    assert.equal(p.run('fixed'), 7);
    assert.equal(p.run('other'), false);
    assert.equal(p.run('fixedObject.ads'), false);
});

test('abort exceptions only suppress their own error events', () => {
    const p = installed('ABORT_READ', ['adMarker']);
    let error;
    try { p.run('adMarker'); } catch (e) { error = e; }
    const listener = p.calls.listeners.find(call => call[1] === 'error')[2];
    let count = 0;
    listener({ error, preventDefault: () => count++ });
    listener({ error: new Error('real site error'), preventDefault: () => count++ });
    assert.equal(count, 1);
});

test('property hooks share ancestors and tolerate primitives and conflicts', () => {
    const p = page([
        ['ABORT_READ', ['ads.first']], ['ABORT_READ', ['ads.second']],
        ['SET', ['ads.first', 'false']],
    ]);
    p.install();
    p.run('ads = 1; ads = { first: 1, second: 2 }');
    assert.throws(() => p.run('ads.first'), { name: 'ReferenceError' });
    assert.throws(() => p.run('ads.second'), { name: 'ReferenceError' });
});

test('nowoif blocks all by default, matches all arguments, and supports negation', () => {
    let p = installed('PREVENT_WINDOW_OPEN', ['']);
    assert.equal(p.run('open("/ad")'), null);
    assert.equal(p.calls.open.length, 0);
    p = installed('PREVENT_WINDOW_OPEN', ['!login']);
    assert.equal(p.run('open("/login")'), 'opened');
    assert.equal(p.run('open("/ad")'), null);
    p = installed('PREVENT_WINDOW_OPEN', ['popup']);
    assert.equal(p.run('open("/allowed", "popup")'), null);
    assert.equal(p.run('open("/login")'), 'opened');
});

test('aeld blocks matching functions and handleEvent objects, preserves other listeners', () => {
    const p = installed('PREVENT_ADD_EVENT_LISTENER', ['click', 'advert']);
    const initial = p.calls.listeners.length;
    p.run('target = new EventTarget(); target.addEventListener("click", function advert() {})');
    p.run('target.addEventListener("click", { handleEvent: function advert() {} })');
    assert.equal(p.calls.listeners.length, initial);
    p.run('target.addEventListener("click", function login() {}, { once: true })');
    p.run('target.addEventListener("load", function advert() {})');
    assert.equal(p.calls.listeners.length, initial + 2);
});

test('aeld installs a single wrapper for several instructions', () => {
    const p = page([
        ['PREVENT_ADD_EVENT_LISTENER', ['click', 'advert']],
        ['PREVENT_ADD_EVENT_LISTENER', ['load', 'notice']],
    ]);
    const original = p.run('EventTarget.prototype.addEventListener');
    p.install();
    assert.notEqual(p.run('EventTarget.prototype.addEventListener'), original);
    p.run('target = new EventTarget(); target.addEventListener("click", function advert() {})');
    p.run('target.addEventListener("load", function notice() {})');
    assert.equal(p.calls.listeners.length, 0);
});

test('nostif defuses callbacks, retains timer IDs, arguments, delay, and other callbacks', () => {
    const p = installed('PREVENT_SET_TIMEOUT', ['advert', '100-250']);
    assert.equal(p.run('setTimeout(function advert() { self.bad = true; }, 200, "x")'), 1);
    p.calls.timers[0][0]();
    assert.equal(p.context.bad, undefined);
    assert.deepEqual(p.calls.timers[0].slice(1), [200, 'x']);
    p.run('setTimeout(function login() { self.good = true; }, 200)');
    p.calls.timers[1][0]();
    assert.equal(p.context.good, true);
    p.run('setTimeout("advert()", 300)');
    assert.equal(p.calls.timers[2][0], 'advert()');
});

test('nostif supports inverted patterns and delays', () => {
    const p = installed('PREVENT_SET_TIMEOUT', ['!login', '!1000']);
    p.run('setTimeout("advert()", 1); setTimeout("login()", 1); setTimeout("advert()", 1000)');
    assert.equal(typeof p.calls.timers[0][0], 'function');
    assert.equal(p.calls.timers[1][0], 'login()');
    assert.equal(p.calls.timers[2][0], 'advert()');
    assert.equal(p.run('open("/login")'), 'opened');
});

test('acs aborts only matching current script reads and writes, including data scripts', () => {
    const p = installed('ABORT_CURRENT_SCRIPT', ['marker', 'adblock', '']);
    p.document.currentScript = new p.Script('adblock detection');
    assert.throws(() => p.run('marker'), { name: 'ReferenceError' });
    assert.throws(() => p.run('marker = 1'), { name: 'ReferenceError' });
    p.document.currentScript = new p.Script('login();');
    assert.equal(p.run('marker = 7; marker'), 7);
    p.document.currentScript = null;
    assert.equal(p.run('marker'), 7);
    p.document.currentScript = new p.Script('', 'data:text/javascript;base64,YWRibG9jaw==');
    assert.throws(() => p.run('marker'), { name: 'ReferenceError' });
});

test('acs source restrictions and coalesced checks preserve unrelated scripts', () => {
    const p = page([
        ['ABORT_CURRENT_SCRIPT', ['marker', 'bad', '/ads/']],
        ['ABORT_CURRENT_SCRIPT', ['marker', 'other', '/ads/']],
    ]);
    p.install();
    p.document.currentScript = new p.Script('bad', 'https://site.example/login.js');
    assert.equal(p.run('marker = 1; marker'), 1);
    p.document.currentScript = new p.Script('other', 'https://site.example/ads.js');
    assert.throws(() => p.run('marker'), { name: 'ReferenceError' });
});

test('json-prune removes own paths with guards and arrays, honours revivers and errors', () => {
    const p = installed('JSON_PRUNE', ['items.[].ad nested.*.ad', 'enabled']);
    const json = '{"enabled":true,"items":[{"ad":1,"ok":2}],"nested":{"x":{"ad":3}}}';
    const result = p.run(`JSON.parse(${JSON.stringify(json)})`);
    assert.equal(JSON.stringify(result), '{"enabled":true,"items":[{"ok":2}],"nested":{"x":{}}}');
    assert.equal(p.run('JSON.parse("{\\"items\\":[{\\"ad\\":1}]}").items[0].ad'), 1);
    assert.equal(p.run('JSON.parse("1", (k,v) => typeof v === "number" ? v+1 : v)'), 2);
    assert.throws(() => p.run('JSON.parse("invalid")'), { name: 'SyntaxError' });
});

test('json-prune also filters Response.json without reading non-JSON bodies', async () => {
    const p = installed('JSON_PRUNE', ['ads', '']);
    const response = new p.context.Response('{"ads":[1],"video":"ok"}');
    assert.deepEqual(await response.json(), { video: 'ok' });
    const text = new p.context.Response('ad text');
    assert.equal(await text.text(), 'ad text');
});

test('json-prune budget exhaustion does not leave partial deletions', () => {
    const p = installed('JSON_PRUNE', ['ads items.[].ads', '']);
    const json = JSON.stringify({
        ads: 1, items: Array.from({ length: 5000 }, () => ({ ads: 1 })),
    });
    const value = p.run(`JSON.parse(${JSON.stringify(json)})`);
    assert.equal(value.ads, 1);
    assert.equal(value.items[0].ads, 1);
});

test('no-fetch-if returns valid empty responses and preserves all other calls', async () => {
    const p = installed('PREVENT_FETCH', ['ads method:HEAD', 'emptyObj']);
    const result = await p.run('fetch("https://site.example/ads", { method: "HEAD" })');
    assert.equal(result.status, 200);
    assert.equal(result.statusText, 'OK');
    assert.equal(result.url, 'https://site.example/ads');
    assert.equal(await result.text(), '{}');
    assert.equal(p.calls.fetch.length, 0);
    assert.equal(await p.run('fetch("https://site.example/login", { method: "POST" })'),
        'native response');
    assert.equal(p.calls.fetch.length, 1);
});

test('no-fetch-if matches Request objects, honours init overrides, and preserves aborted signals',
    async () => {
        const p = installed('PREVENT_FETCH', ['method:HEAD', '']);
        p.context.request = new Request('https://site.example/ads', { method: 'HEAD' });
        assert.equal((await p.run('fetch(request)')).status, 200);
        assert.equal(await p.run('fetch(request, { method: "POST" })'), 'native response');
        const controller = new AbortController();
        controller.abort();
        p.context.signal = controller.signal;
        await assert.rejects(p.run('fetch(request, { signal })'), { name: 'AbortError' });
    });

test('scopes, exceptions, duplicates, and list order are respected', () => {
    const p = page([
        ['SET', ['marker', 'false']], ['SET', ['marker', 'false']],
        ['SET', ['other', 'true'], 'site.example', ['sub.site.example']],
        ['SET', ['marker', 'false'], 'sub.site.example', [], true],
    ], { host: 'sub.site.example' });
    p.install();
    assert.equal(p.run('typeof marker'), 'undefined');
    assert.equal(p.run('typeof other'), 'undefined');
    assert.equal(p.run('Object.getOwnPropertyDescriptor(self, "marker")'), undefined);
});

test('blanket exceptions and private or unrelated origins stay untouched', () => {
    for (const options of [
        { host: 'other.example' }, { host: 'site.example.attacker.example' },
        { host: 'appassets.androidplatform.net' },
        { host: 'site.example', protocol: 'file:' },
        { host: '', protocol: 'about:' },
    ]) {
        const p = installed('SET', ['marker', 'false'], options);
        assert.equal(p.run('typeof marker'), 'undefined');
        assert.equal(p.calls.listeners.length, 0);
    }
    const p = page([
        ['SET', ['marker', 'false']], [null, [], '', [], true],
    ]);
    p.install();
    assert.equal(p.run('typeof marker'), 'undefined');
});

test('cross-origin frames and navigation use independent matching snapshots', () => {
    const rules = [['SET', ['marker', 'false'], 'frame.example']];
    const top = page(rules);
    const frame = page(rules, { host: 'frame.example' });
    const next = page(rules, { host: 'login.example' });
    top.install(); frame.install(); next.install();
    assert.equal(top.run('typeof marker'), 'undefined');
    assert.equal(frame.run('marker'), false);
    assert.equal(next.run('typeof marker'), 'undefined');
});

test('only selected function hooks are installed and the per-document rule cap is enforced', () => {
    const p = installed('SET', ['marker', 'false']);
    assert.equal(p.calls.listeners.length, 0);
    assert.equal(p.run('open("/login")'), 'opened');
    assert.equal(p.calls.timers.length, 0);
    const many = page(Array.from({ length: 1040 }, (_, i) => ['SET', ['p' + i, 'false']]));
    many.install();
    assert.equal(many.run('p1023'), false);
    assert.equal(many.run('typeof p1024'), 'undefined');
});

test('filter data cannot escape JSON serialization into source code', () => {
    const p = installed('PREVENT_WINDOW_OPEN', ['"); self.compromised = true; //']);
    assert.equal(p.run('open("/login")'), 'opened');
    assert.equal(p.context.compromised, undefined);
});

test('the host gate avoids loading instruction data on unrelated or private origins', () => {
    for (const options of [
        { host: 'unrelated.example' }, { host: 'site.example.attacker.example' },
        { host: 'appassets.androidplatform.net' }, { protocol: 'about:' },
    ]) {
        const p = page([], options);
        const routes = { hosts: 'site.example\t[0]\n', entities: '', ancestors: '',
            ancestorEntities: '', regex: '[]', exceptions: [], limit: 1024 };
        p.run(`${runtime}(${JSON.stringify(routes)}, 'unread invalid JSON');`);
        assert.equal(p.calls.listeners.length, 0);
    }
});

test('read-only accessors remain unchanged while nested configurable properties are hooked', () => {
    const p = page([
        ['SET', ['readOnly', 'false']], ['ABORT_READ', ['navigator.marker']],
    ], { before: 'Object.defineProperty(self, "readOnly", ' +
        '{ value: 7, configurable: true, writable: false });' +
        'const nav = {}; Object.defineProperty(self, "navigator", ' +
        '{ configurable: true, get() { return nav; } });' });
    p.install();
    assert.equal(p.run('readOnly'), 7);
    assert.equal(p.run('Object.getOwnPropertyDescriptor(self, "readOnly").writable'), false);
    assert.throws(() => p.run('navigator.marker'), { name: 'ReferenceError' });
});

test('terminal hooks cannot be replaced through defineProperty', () => {
    const p = installed('SET', ['marker', 'false']);
    assert.throws(() => p.run('Object.defineProperty(self, "marker", { value: true })'),
        { name: 'TypeError' });
    assert.equal(p.run('marker'), false);
});

test('regex matchers compile once and match consistently across successive calls', () => {
    const p = installed('PREVENT_WINDOW_OPEN', ['/AD|promo/i']);
    assert.equal(p.run('open("/ad")'), null);
    assert.equal(p.run('open("/ad")'), null);
    assert.equal(p.run('open("/login")'), 'opened');
});

test('prototype-looking hostnames cannot alter the routing table prototype', () => {
    const p = page([['SET', ['marker', 'false'], '__proto__']], { host: '__proto__' });
    p.install();
    assert.equal(p.run('marker'), false);
});

test('predefined function constants keep the trapped property name and native-style string', () => {
    const p = installed('SET', ['advertCallback', 'noopFunc']);
    assert.equal(p.run('advertCallback.name'), 'advertCallback');
    assert.equal(p.run('advertCallback.toString()'),
        'function advertCallback() { [native code] }');
    assert.equal(p.run('advertCallback()'), undefined);
});

test('nested property traps follow the actual value produced by an existing setter', () => {
    const p = installed('SET', ['player.ads', 'false'], { before:
        'let actualPlayer = {}; Object.defineProperty(self, "player", {' +
        'configurable: true, get() { return actualPlayer; },' +
        'set(value) { actualPlayer = { ...value }; } });' });
    assert.equal(p.run('player = { ads: true, title: "Video" }; player.ads'), false);
    assert.equal(p.run('player.title'), 'Video');
});

test('constants override configurable getters without extra reads or observers', () => {
    const p = page([['SET', ['navigator.globalPrivacyControl', 'false']]], { before: `
        nativeReads = 0;
        const nav = {};
        Object.defineProperty(nav, 'globalPrivacyControl', {
            configurable: true, get() { nativeReads++; return true; },
        });
        Object.defineProperty(self, 'navigator', { configurable: true, get() { return nav; } });
    ` });
    p.install();
    assert.equal(p.run('nativeReads'), 1);
    assert.equal(p.run('navigator.globalPrivacyControl'), false);
    assert.equal(p.run('navigator.globalPrivacyControl'), false);
    assert.equal(p.run('nativeReads'), 1);
    assert.equal(p.calls.listeners.length, 0);
    assert.equal(p.calls.timers.length, 0);
});

test('prototype constants tolerate WebIDL receiver checks and apply to future instances', () => {
    const p = page([
        ['SET', ['Navigator.prototype.globalPrivacyControl', 'false']],
        ['SET', ['navigator.globalPrivacyControl', 'false']],
    ], { before: `
        class Navigator {}
        Object.defineProperty(Navigator.prototype, 'globalPrivacyControl', {
            configurable: true, get() {
                if (!(this instanceof Navigator)) { throw new TypeError('Illegal invocation'); }
                return true;
            },
        });
        self.Navigator = Navigator;
        Object.defineProperty(self, 'navigator', {
            configurable: true, get() { return currentNavigator; },
        });
        currentNavigator = new Navigator();
    ` });
    p.install();
    assert.equal(p.run('navigator.globalPrivacyControl'), false);
    assert.equal(p.run('new Navigator().globalPrivacyControl'), false);
    assert.equal(p.run('currentNavigator = new Navigator(); navigator.globalPrivacyControl'), false);
});

test('constant installation preserves incompatible getters and non-configurable properties', () => {
    const p = page([
        ['SET', ['objectValue', 'false']], ['SET', ['fixed', 'false']],
        ['SET', ['allowed', 'false']], ['SET', ['allowed', 'true']],
    ], { before: `
        originalGetter = () => ({ token: 'retained' });
        Object.defineProperty(self, 'objectValue', { configurable: true, get: originalGetter });
        Object.defineProperty(self, 'fixed', { configurable: false, get() { return true; } });
        Object.defineProperty(self, 'allowed', { configurable: true, get() { return true; } });
    ` });
    p.install();
    assert.equal(p.run('Object.getOwnPropertyDescriptor(self, "objectValue").get === originalGetter'), true);
    assert.equal(p.run('objectValue.token'), 'retained');
    assert.equal(p.run('fixed'), true);
    assert.equal(p.run('allowed'), false);
});

test('released getter constants retain the original receiver and trusted constants stay set', () => {
    const p = page([
        ['SET', ['holder.flag', 'false']], ['TRUSTED_SET', ['holder.trusted', 'false']],
    ], { before: `
        holder = {};
        for (const key of ['flag', 'trusted']) {
            Object.defineProperty(holder, key, { configurable: true, get() {
                if (this !== holder) { throw new TypeError('Wrong receiver'); }
                return true;
            } });
        }
    ` });
    p.install();
    assert.equal(p.run('holder.flag'), false);
    assert.equal(p.run('holder.flag = "release"; holder.flag'), true);
    assert.equal(p.run('holder.trusted = "retained"; holder.trusted'), false);
});
