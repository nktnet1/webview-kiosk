import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';
import { page, installed } from './scriptlet-fixture.mjs';
import { domFixture } from './scriptlet-dom-fixture.mjs';

function store(initial = {}) {
    const data = new Map(Object.entries(initial));
    return { data, get length() { return data.size; }, key: i => [...data.keys()][i],
        setItem: (key, value) => data.set(key, String(value)),
        removeItem: key => data.delete(key), getItem: key => data.get(key) ?? null };
}
function dom(rules) {
    const d = domFixture();
    const p = page(rules, { globals: d.globals });
    return { ...d, p };
}

test('interval defusing retains native IDs, args and unrelated login callbacks', () => {
    const p = installed('PREVENT_SET_INTERVAL', ['advert', '100-500']);
    assert.equal(p.run('setInterval(function advert(){}, 200, "x")'), 1);
    assert.equal(p.calls.intervals[0][0].toString(), 'function() {}');
    assert.deepEqual(p.calls.intervals[0].slice(1), [200, 'x']);
    p.run('setInterval(function login(){}, 200)');
    assert.match(p.calls.intervals[1][0].toString(), /login/);
});

test('boosters apply exact delays in reverse list order without changing extra args', () => {
    const p = page([
        ['ADJUST_SET_TIMEOUT', ['advert', '1000', '0.5']],
        ['ADJUST_SET_TIMEOUT', ['advert', '1000', '2']],
        ['ADJUST_SET_INTERVAL', ['', '*', '0.01']],
    ]);
    p.install();
    p.run('setTimeout(function advert(){}, 1000, 7); setTimeout(function login(){}, 1000)');
    assert.equal(p.calls.timers[0][1], 2000);
    assert.equal(p.calls.timers[0][2], 7);
    assert.equal(p.calls.timers[1][1], 1000);
    p.run('setInterval(function(){}, 200)');
    assert.equal(p.calls.intervals[0][1], 2);
});

test('timer defusers and boosters share a hook and preserve unusual native arguments', () => {
    const p = page([
        ['PREVENT_SET_TIMEOUT', ['advert', '']], ['ADJUST_SET_TIMEOUT', ['', '*', '0.5']],
    ]);
    p.install(); p.run('setTimeout(function advert(){}, 100)');
    assert.equal(p.calls.timers.length, 1);
    assert.equal(p.calls.timers[0][1], 50);
    p.run('setTimeout(null, { valueOf(){ return 5; } })');
    assert.equal(p.calls.timers[1][0], null);
    assert.equal(typeof p.calls.timers[1][1], 'object');
});

test('animation-frame defusing replaces only matching callbacks and keeps IDs', () => {
    const p = installed('PREVENT_ANIMATION_FRAME', ['advert']);
    assert.equal(p.run('requestAnimationFrame(function advert(){ throw 1; })'), 1);
    assert.doesNotThrow(() => p.flushFrames());
    p.run('requestAnimationFrame(function login(){ self.loggedIn = true; })'); p.flushFrames();
    assert.equal(p.run('loggedIn'), true);
});

test('eval patterns block matching page strings and preserve nonstrings and native errors', () => {
    const p = installed('PREVENT_EVAL', ['advert']);
    assert.equal(p.run('eval("self.advert = 7")'), undefined);
    assert.equal(p.run('typeof advert'), 'undefined');
    assert.equal(p.run('eval("self.sessionToken = 9"); sessionToken'), 9);
    assert.equal(p.run('eval(4)'), 4);
    assert.throws(() => p.run('eval("!")'), { name: 'SyntaxError' });
    const q = installed('PREVENT_EVAL', ['']);
    assert.equal(q.run('eval(4)'), undefined);
});

test('stack aborts matching nested reads and writes with shared property conflict handling', () => {
    const p = page([
        ['ABORT_STACK_TRACE', ['ad.detect', 'adCheck']], ['SET', ['safe', 'false']],
    ]);
    p.install(); p.run('ad = { detect: 5 }');
    assert.equal(p.run('ad.detect'), 5);
    assert.throws(() => p.run('function adCheck(){ return ad.detect; } adCheck()'),
        { name: 'ReferenceError' });
    assert.throws(() => p.run('function adCheck(){ ad.detect = 9; } adCheck()'),
        { name: 'ReferenceError' });
    assert.equal(p.run('safe'), false);
    const q = page([['SET', ['flag', 'false']], ['ABORT_STACK_TRACE', ['flag', 'adCheck']]]);
    q.install(); assert.equal(q.run('function adCheck(){ return flag; } adCheck()'), false);
});

test('storage sets safe strings and removes exact keys or bounded regex matches', () => {
    const local = store({ ads: '1', 'ads.extra': '2', login: 'token' });
    const session = store();
    const p = page([
        ['SET_LOCAL_STORAGE', ['ads', '$remove$']],
        ['SET_LOCAL_STORAGE', ['consent', 'false']],
        ['SET_SESSION_STORAGE', ['cache', '{}']],
    ], { globals: { localStorage: local, sessionStorage: session } });
    p.install();
    assert.equal(local.getItem('ads'), null);
    assert.equal(local.getItem('ads.extra'), '2');
    assert.equal(local.getItem('login'), 'token');
    assert.equal(local.getItem('consent'), 'false');
    assert.equal(session.getItem('cache'), '{}');
    installed('SET_LOCAL_STORAGE', ['/^ads/', '$remove$'], { globals: { localStorage: local } });
    assert.equal(local.getItem('ads.extra'), null);
});

test('blocked or huge storage fails open and does not prevent other scriptlets', () => {
    const p = page([
        ['SET_LOCAL_STORAGE', ['x', 'true']], ['SET', ['ok', 'true']],
    ], { before: 'Object.defineProperty(self,"localStorage",{get(){throw new Error("denied")}})' });
    p.install(); assert.equal(p.run('ok'), true);
    const big = store(Object.fromEntries(Array.from({ length: 1025 }, (_, i) => [i, 'x'])));
    installed('SET_LOCAL_STORAGE', ['/^/', '$remove$'], { globals: { localStorage: big } });
    assert.equal(big.length, 1025);
});

function cookieDocument() {
    const jar = new Map([['session', 'token']]);
    const writes = [];
    return { jar, writes, currentScript: null,
        get cookie() { return [...jar].map(([k, v]) => k + '=' + v).join('; '); },
        set cookie(value) {
            writes.push(value);
            const [pair] = value.split(';'); const i = pair.indexOf('=');
            if (value.includes('Max-Age=-')) { jar.delete(pair.slice(0, i)); }
            else { jar.set(pair.slice(0, i), pair.slice(i + 1)); }
        } };
}
test('cookies use encoded values, secure prefixes and reload only after a real change', () => {
    const document = cookieDocument(); let reloads = 0;
    const globals = { document, location: { protocol: 'https:', hostname: 'site.example',
        href: 'https://site.example/', reload() { reloads++; } } };
    installed('SET_COOKIE_RELOAD', ['consent', 'false', '/'], { globals });
    assert.equal(document.jar.get('consent'), 'false'); assert.equal(reloads, 1);
    installed('SET_COOKIE_RELOAD', ['consent', 'false', '/'], { globals });
    assert.equal(reloads, 1);
    installed('SET_COOKIE', ['__Secure-choice', 'true', '/'], { globals });
    assert.match(document.writes.at(-1), /; Secure$/);
    assert.equal(document.jar.get('session'), 'token');
});

test('cookie removal matches names only and repeats on beforeunload', () => {
    const document = cookieDocument(); document.jar.set('advert', 'yes');
    const p = installed('REMOVE_COOKIE', ['advert'], { globals: { document } });
    assert.equal(document.jar.has('advert'), false);
    assert.equal(document.jar.get('session'), 'token');
    document.jar.set('advert', 'again'); p.emit('beforeunload');
    assert.equal(document.jar.has('advert'), false);
});

test('cookie options reload only after saving and respect existing and empty cookies', () => {
    const document = cookieDocument(); let reloads = 0;
    const globals = { document, location: { protocol: 'https:', hostname: 'site.example',
        reload() { reloads++; } } };
    const args = ['GPC', '1', '', 'reload', '1'];
    installed('SET_COOKIE', args, { globals });
    assert.equal(document.jar.get('GPC'), '1'); assert.equal(reloads, 1);
    installed('SET_COOKIE', args, { globals });
    assert.equal(reloads, 1); assert.equal(document.writes.length, 1);
    document.jar.set('GPC', '');
    installed('SET_COOKIE', [...args, 'dontOverwrite', '1'], { globals });
    assert.equal(document.jar.get('GPC'), ''); assert.equal(reloads, 1);
    installed('SET_COOKIE_RELOAD', ['GPC', '1', 'none', 'reload', '0'], { globals });
    assert.equal(reloads, 1); assert.equal(document.writes.at(-1), 'GPC=1');
    const refused = { currentScript: null, get cookie() { return ''; }, set cookie(value) {} };
    installed('SET_COOKIE', args, { globals: { ...globals, document: refused } });
    assert.equal(reloads, 1);
    const denied = { currentScript: null, get cookie() { return ''; },
        set cookie(value) { throw new DOMException('Denied', 'SecurityError'); } };
    installed('SET_COOKIE', args, { globals: { ...globals, document: denied } });
    assert.equal(reloads, 1);
});

const fixedCookieTime = `
    Date = class extends Date {
        constructor(...args) { super(...(args.length ? args : ['2024-02-29T12:34:56.000Z'])); }
    };
`;
test('cookie encoding preserves valid bytes and encodes unsafe names and values', () => {
    const document = cookieDocument();
    installed('SET_COOKIE', ['consent$state', 'true', ''], { globals: { document } });
    assert.equal(document.writes.at(-1), 'consent$state=true; path=/');
    installed('SET_COOKIE', ['space name', 'true', ''], { globals: { document } });
    assert.equal(document.writes.at(-1), 'space%20name=true; path=/');
    installed('TRUSTED_SET_COOKIE', ['token', 'abc=+/"value",', '', ''], { globals: { document } });
    assert.equal(document.writes.at(-1), 'token=abc=+/"value",; path=/; Secure');
    installed('TRUSTED_SET_COOKIE', ['token', 'unsafe;value\\', '', 'none'], { globals: { document } });
    assert.equal(document.writes.at(-1), 'token=unsafe%3Bvalue%5C; Secure');
});

test('cookie encoding uses native intrinsics when other selected property hooks replace them', () => {
    const document = cookieDocument();
    const p = page([
        ['SET', ['RegExp.prototype.test', 'falseFunc']],
        ['SET', ['encodeURIComponent', 'noopFunc']],
        ['SET_COOKIE', ['space name', 'true', '']],
        ['TRUSTED_SET_COOKIE', ['token', 'unsafe;value', '', '']],
    ], { globals: { document } });
    p.install();
    assert.deepEqual(document.writes, ['space%20name=true; path=/', 'token=unsafe%3Bvalue; path=/; Secure']);
});

test('trusted cookie expiry, path, literal domain and flags use their own positions', () => {
    for (const [expiry, expected] of [
        ['1day', 'Fri, 01 Mar 2024 12:34:56 GMT'],
        ['1year', 'Sat, 01 Mar 2025 12:34:56 GMT'],
        ['3600', 'Thu, 29 Feb 2024 13:34:56 GMT'],
        ['0', 'Thu, 29 Feb 2024 12:34:56 GMT'],
    ]) {
        const document = cookieDocument();
        installed('TRUSTED_SET_COOKIE', ['choice', 'abc=', expiry, 'none',
            'domain', 'site.example'], { globals: { document }, before: fixedCookieTime });
        assert.equal(document.writes.at(-1), `choice=abc=; expires=${expected}; domain=site.example; Secure`);
    }
    const document = cookieDocument();
    document.jar.set('choice', 'abc=');
    installed('TRUSTED_SET_COOKIE', ['choice', 'abc=', '1day', ''],
        { globals: { document }, before: fixedCookieTime });
    assert.match(document.writes.at(-1), /; expires=.*; path=\/; Secure$/);
    assert.equal(document.writes.length, 1, 'Same-value writes can update expiry or scope');
});

test('trusted cookie placeholders use captured Date methods before expiry adjustment', () => {
    const document = cookieDocument();
    const p = page([
        ['SET', ['Date', 'undefined']],
        ['TRUSTED_SET_COOKIE', ['time', '$now$/$currentISODate$/$currentDate$', '1year', '']],
    ], { globals: { document }, before: fixedCookieTime });
    p.install();
    assert.equal(document.jar.get('time'),
        '1709210096000/2024-02-29T12:34:56.000Z/Thu, 29 Feb 2024 12:34:56 GMT');
    assert.match(document.writes.at(-1), /expires=Sat, 01 Mar 2025 12:34:56 GMT/);
});

test('trusted reload flags avoid loops and failed Secure writes never reload', () => {
    let reloads = 0;
    const document = cookieDocument();
    const location = { protocol: 'https:', hostname: 'site.example', reload() { reloads++; } };
    const args = ['choice', 'abc=', '1day', '', 'reload', '1'];
    installed('TRUSTED_SET_COOKIE', args, { globals: { document, location }, before: fixedCookieTime });
    installed('TRUSTED_SET_COOKIE', args, { globals: { document, location }, before: fixedCookieTime });
    assert.equal(reloads, 1); assert.equal(document.writes.length, 1);
    document.jar.set('choice', '');
    installed('TRUSTED_SET_COOKIE_RELOAD', ['choice', 'abc=', '', '', 'dontOverwrite', '1'],
        { globals: { document, location } });
    assert.equal(reloads, 1); assert.equal(document.jar.get('choice'), '');
    const refused = { currentScript: null, get cookie() { return ''; }, set cookie(value) {} };
    installed('TRUSTED_SET_COOKIE', args,
        { globals: { document: refused, location: { ...location, protocol: 'http:' } }, before: fixedCookieTime });
    assert.equal(reloads, 1);
});

test('cached Kotlin-produced cookie routing preserves arguments, exceptions and reload guards',
    { skip: !process.env.SCRIPTLET_OUTPUT_DIR }, () => {
        const source = readFileSync(join(process.env.SCRIPTLET_OUTPUT_DIR, 'cookie-injection.js'), 'utf8');
        const document = cookieDocument(); let reloads = 0;
        const location = { protocol: 'https:', hostname: 'site.example', reload() { reloads++; } };
        for (let i = 0; i < 2; i++) {
            page([], { globals: { document, location }, before: fixedCookieTime }).run(source);
        }
        assert.equal(document.writes.length, 1); assert.equal(reloads, 1);
        assert.equal(document.writes[0],
            'token=abc=; expires=Sat, 01 Mar 2025 12:34:56 GMT; path=/; domain=site.example; Secure');
        const excluded = cookieDocument();
        page([], { host: 'safe.site.example', globals: { document: excluded }, before: fixedCookieTime }).run(source);
        assert.equal(excluded.writes.length, 0);
    });

test('call-nothrow retains receivers and results while suppressing target throws', () => {
    const p = installed('CALL_NOTHROW', ['api.run'], { before:
        'api={value:5,run(x){if(x<0)throw Error("bad");return this.value+x;}};' });
    assert.equal(p.run('api.run(2)'), 7);
    assert.equal(p.run('api.run(-1)'), undefined);
    assert.throws(() => p.run('throw Error("login failed")'), /login failed/);
    assert.doesNotThrow(() => installed('CALL_NOTHROW', ['missing.run']));
});

test('canvas hooks only matching context types', () => {
    const p = installed('PREVENT_CANVAS', ['webgl'], { before:
        'HTMLCanvasElement=class{getContext(type){return type+" native";}};' });
    assert.equal(p.run('new HTMLCanvasElement().getContext("webgl")'), null);
    assert.equal(p.run('new HTMLCanvasElement().getContext("2d")'), '2d native');
});

test('freezing innerHTML scopes by element and pattern, keeps native getters and objects', () => {
    const d = dom([['PREVENT_ELEMENT_PROPERTY', ['innerHTML', '.advert', 'blocked']]]);
    const node = d.add(new d.Element('div', { class: 'advert' })); d.p.context.node = node;
    d.p.install(); d.p.run('node.innerHTML="blocked"'); assert.equal(node.innerHTML, '');
    d.p.run('node.innerHTML="login"'); assert.equal(node.innerHTML, 'login');
    node.classes.clear(); d.p.run('node.innerHTML="blocked"');
    assert.equal(node.innerHTML, 'blocked');
    d.p.run('node.innerHTML={toString(){return "trusted"}}');
    assert.equal(node.innerHTML, 'trusted');
});

test('nonconfigurable DOM properties fail open without disturbing remaining instructions', () => {
    const d = dom([
        ['PREVENT_ELEMENT_PROPERTY', ['innerHTML', '', '']], ['SET', ['other', 'false']],
    ]);
    const desc = Object.getOwnPropertyDescriptor(d.Element.prototype, 'innerHTML');
    Object.defineProperty(d.Element.prototype, 'innerHTML', { ...desc, configurable: false });
    d.p.install(); assert.equal(d.p.run('other'), false);
});

test('spoof-css changes computed values and rectangles without changing real styles', () => {
    const d = dom([['SPOOF_CSS', ['.advert', 'display', 'none', 'width', '50px']]]);
    const node = d.add(new d.Element('div', { class: 'advert' })); d.p.context.node = node;
    d.p.install();
    assert.equal(d.p.run('getComputedStyle(node).display'), 'none');
    assert.equal(d.p.run('getComputedStyle(node).getPropertyValue("width")'), '50px');
    assert.equal(node.getBoundingClientRect().width, 50);
    node.classes.clear(); assert.equal(node.getBoundingClientRect().width, 10);
    assert.equal(d.p.run('getComputedStyle(node).display'), 'block');
});

test('DOM instructions share one observer and bounded frame work, including bfcache resume', () => {
    const d = dom([
        ['SET_ATTRIBUTE', ['.advert', 'data-off', 'true']],
        ['REMOVE_ATTRIBUTE', ['onclick', '.advert', 'stay']],
        ['REMOVE_CLASS', ['hidden', '.advert', 'stay']],
        ['PREVENT_DIALOG', ['']],
    ]);
    const node = d.add(new d.Element('div', { class: 'advert hidden', onclick: 'bad()' }));
    const dialog = d.add(new d.Element('dialog')); dialog.open = true;
    d.p.install();
    assert.equal(d.observers.length, 1);
    assert.equal(node.getAttribute('data-off'), null);
    assert.equal(dialog.closed, 1);
    d.ready(d.p, 'interactive');
    assert.equal(node.hasAttribute('onclick'), false);
    assert.equal(node.classList.contains('hidden'), false);
    d.ready(d.p, 'complete');
    assert.equal(node.getAttribute('data-off'), 'true');
    d.mutate(); d.mutate(); assert.equal(d.p.calls.frames.size, 1);
    d.p.emit('pagehide'); assert.equal(d.observers[0].active, false);
    assert.equal(d.p.calls.frames.size, 0);
    d.p.emit('pageshow', { persisted: true }); assert.equal(d.observers[0].active, true);
});

test('attribute removal obeys lifecycle options and attribute copies stay literal', () => {
    const d = dom([
        ['SET_ATTRIBUTE', ['div', 'data-number', '[data-source]']],
        ['REMOVE_ATTRIBUTE', ['title', 'div', 'complete stay']],
    ]);
    const node = d.add(new d.Element('div', { title: 'ad', 'data-source': '0007' }));
    d.p.install(); assert.equal(node.getAttribute('data-number'), null);
    assert.equal(node.getAttribute('title'), 'ad');
    d.ready(d.p, 'complete');
    assert.equal(node.getAttribute('data-number'), '0007');
    assert.equal(node.hasAttribute('title'), false);
    node.setAttribute('title', 'again'); d.mutate(); d.p.flushFrames();
    assert.equal(node.hasAttribute('title'), false);
});

test('remove-node-text handles new scripts until interactive and preserves current scripts',
    () => {
        const d = dom([['REMOVE_NODE_TEXT', ['script', 'adCode']]]);
        const old = d.add(new d.Script('adCode()')); d.p.install();
        assert.equal(old.textContent, '');
        const current = d.add(new d.Script('adCode()')); d.document.currentScript = current;
        d.mutate([current]); assert.equal(current.textContent, 'adCode()');
        d.document.currentScript = null;
        const next = d.add(new d.Script('adCode()')); d.mutate([next]);
        assert.equal(next.textContent, '');
        d.ready(d.p, 'interactive');
        const late = d.add(new d.Script('adCode()')); d.mutate([late]);
        assert.equal(late.textContent, 'adCode()');
    });

test('href sanitizer supports text, attribute and query sources and rejects script URLs', () => {
    for (const source of ['text', '[title]', '?to']) {
        const d = dom([['HREF_SANITIZER', ['a', source]]]);
        const node = d.add(new d.Element('a', { href:
            'https://tracker.example/?to=https%3A%2F%2Fdestination.example%2F',
        title: 'https://destination.example/' }));
        node.textContent = 'https://destination.example/';
        d.p.install(); d.ready(d.p, 'interactive');
        assert.equal(node.href, 'https://destination.example/');
    }
    const d = dom([['HREF_SANITIZER', ['a', 'text']]]);
    const node = d.add(new d.Element('a', { href: 'https://safe.example/' }));
    node.textContent = 'javascript:alert(1)'; d.p.install(); d.ready(d.p, 'interactive');
    assert.equal(node.href, 'https://safe.example/');
});

test('dialog prevention scopes by selector suffix', () => {
    const d = dom([['PREVENT_DIALOG', ['.advert']]]);
    const ad = d.add(new d.Element('dialog', { class: 'advert' })); ad.open = true;
    const login = d.add(new d.Element('dialog')); login.open = true;
    d.p.install(); assert.equal(ad.open, false); assert.equal(login.open, true);
});

test('newtab links and multiup cancel only matching clicks and reject unsafe destinations', () => {
    const d = dom([['DISABLE_NEWTAB_LINKS', []], ['MULTIUP', []]]); d.p.install();
    const anchor = new d.Element('a', { target: '_blank', href: 'https://ads.example/' });
    const child = new d.Element('span'); child.parentNode = anchor;
    let prevented = 0;
    const emit = node => d.p.emit('click', { target: node, preventDefault() { prevented++; },
        stopPropagation() {} }, d.document);
    emit(child); assert.equal(anchor.hasAttribute('target'), false); assert.equal(prevented, 1);
    const form = new d.Element('form'); const button = new d.Element('button', {
        link: 'javascript:alert(1)' }); button.parentElement = form;
    emit(button); assert.equal(prevented, 1);
    button.setAttribute('link', 'https://download.example/file'); emit(button);
    assert.equal(prevented, 2);
    assert.equal(d.p.context.location.href, 'https://download.example/file');
});

test('refresh prevention waits for load and uses protected timers', () => {
    const d = dom([['PREVENT_REFRESH', ['']], ['PREVENT_SET_TIMEOUT', ['stop', '']]]);
    d.add(new d.Element('meta', { 'http-equiv': 'refresh', content: '10;url=/ads' }));
    d.p.install(); assert.equal(d.p.calls.timers.length, 0); d.p.emit('load');
    assert.equal(d.p.calls.timers.length, 1); assert.equal(d.p.calls.timers[0][1], 5000);
    d.p.calls.timers[0][0](); assert.equal(d.p.calls.stopped, 1);
});

test('explicit refresh delays stop synchronously during load rather than queueing a timer', () => {
    const d = dom([['PREVENT_REFRESH', ['0']]]);
    d.add(new d.Element('meta', { 'http-equiv': 'refresh', content: '0;url=/ads' }));
    d.p.install(); d.p.emit('load');
    assert.equal(d.p.calls.stopped, 1);
    assert.equal(d.p.calls.timers.length, 0);
});

test('window name is cleared only in top frames; alert defusing keeps confirm unchanged', () => {
    const p = page([['CLEAR_WINDOW_NAME', []], ['ALERT_BUSTER', []]], { before:
        'name="ad"; alert=()=>"shown"; confirm=()=>true;' }); p.install();
    assert.equal(p.run('name'), ''); assert.equal(p.run('alert("ads")'), undefined);
    assert.equal(p.run('confirm("login")'), true);
    const q = installed('CLEAR_WINDOW_NAME', [], { before: 'name="frame";top={};' });
    assert.equal(q.run('name'), 'frame');
});

test('WebRTC defusing and allow patterns are conditional and preserve good native configurations',
    () => {
        const before = 'RTCPeerConnection=class{constructor(c){this.config=c;}' +
            'createDataChannel(n,o){return {name:n,options:o};}};';
        const p = installed('WEBRTC_IF', ['good.example'], { before });
        assert.equal(p.run('new RTCPeerConnection({iceServers:[{urls:"stun:good.example"}]})' +
            '.config.iceServers[0].urls'), 'stun:good.example');
        assert.equal(p.run('var peer=new RTCPeerConnection({iceServers:[{urls:"stun:bad"}]});' +
            'peer.config'), undefined);
        assert.equal(p.run('Object.keys(peer.createDataChannel("x",{advert:true})' +
            '.options).length'), 0);
        const q = installed('PREVENT_WEBRTC', [], { before });
        assert.equal(q.run('typeof new RTCPeerConnection().createDataChannel("x").send'),
            'function');
        assert.doesNotThrow(() => installed('WEBRTC_IF', ['good.example']));
    });

test('Navigation API hook is inactive when unavailable and preserves user and noncancelable events',
    () => {
        assert.doesNotThrow(() => installed('PREVENT_NAVIGATION', ['ads.example']));
        const p = page([['PREVENT_NAVIGATION', ['ads.example']]], { before:
            'navigation=new EventTarget();' }); p.install();
        let prevented = 0;
        const emit = changes => p.emit('navigate', { destination: { url: 'https://ads.example/' },
            userInitiated: false, cancelable: true, preventDefault() { prevented++; }, ...changes },
        p.context.navigation);
        emit({ userInitiated: true }); emit({ cancelable: false });
        emit({ destination: { url: 'https://login.example/' } }); assert.equal(prevented, 0);
        emit({}); assert.equal(prevented, 1);
    });

test('BAB defuses verified signatures and timeout strings but retains ordinary page code', () => {
    const p = installed('PREVENT_BAB', []);
    assert.equal(p.run('eval("self.blockadblock=true")'), undefined);
    assert.equal(p.run('eval("self.babasbm=true")'), undefined);
    assert.equal(p.run('eval("self.session=3");session'), 3);
    p.run('setTimeout("f.bab_elementid)",100)');
    assert.equal(p.calls.timers[0][0].toString(), 'function() {}');
});

const shims = [
    ['amazon_ads.js', 'amznads.hasAds()', undefined],
    ['adthrive_abd.js', 'document.cookie.startsWith("__adblocker=false;")', true],
    ['doubleclick_instream_ad_status.js', 'google_ad_status', 1],
    ['fingerprint2.js', 'typeof Fingerprint2.get', 'function'],
    ['fingerprint3.js', 'typeof FingerprintJS.load', 'function'],
    ['google-analytics_analytics.js', 'typeof ga.getAll', 'function'],
    ['google-analytics_ga.js', '_gat._getTracker()._getLinkerUrl("https://login.example/")',
        'https://login.example/'],
    ['googletagservices_gpt.js', 'googletag.pubads().getSlots().length', 0],
    ['nitropay_ads.js', 'typeof nitroAds.createAd', 'function'],
    ['nofab.js', 'typeof fuckAdBlock.onNotDetected', 'function'],
    ['popads.js', 'typeof PopAds', 'undefined'],
    ['popads-dummy.js', 'typeof PopAds', 'object'],
    ['prebid-ads.js', 'canRunAds && !isAdBlockActive', true],
    ['sensors-analytics.js', 'typeof sensorsDataAnalytic201505.track', 'function'],
];
for (const [resource, probe, expected] of shims) {
    test('bundled ' + resource + ' shim executes its API', () => {
        const p = installed('RESOURCE', [resource]);
        assert.equal(p.run(probe), expected);
    });
}

test('shim queues and callbacks run through protected native timers', () => {
    const p = page([
        ['RESOURCE', ['google-analytics_analytics.js']], ['PREVENT_SET_TIMEOUT', ['', '0-1000']],
    ], { before: 'ga=()=>{};ga.q=[[function(){self.ready=true}]];' +
        'dataLayer=[{eventCallback(){self.eventDone=true}}];' });
    p.install();
    for (const row of p.calls.timers) { if (typeof row[0] === 'function') { row[0](); } }
    assert.equal(p.run('ready'), true);
    assert.equal(p.run('eventDone'), true);
    const q = installed('RESOURCE', ['nofab.js']);
    assert.equal(q.run('var called=false;fuckAdBlock.onNotDetected(()=>called=true);called'), true);
});

test('exceptions, exclusions and frame host routing apply to additional instructions too', () => {
    const p = page([
        ['SET_LOCAL_STORAGE', ['consent', 'false']],
        ['SET_LOCAL_STORAGE', ['consent', 'false'], 'site.example', [], true],
        ['ADJUST_SET_TIMEOUT', ['', '*', '0.5'], 'site.example', ['frame.site.example']],
    ], { host: 'frame.site.example', globals: { localStorage: store() } });
    p.install(); assert.equal(p.context.localStorage.length, 0);
    p.run('setTimeout(function(){}, 1000)'); assert.equal(p.calls.timers[0][1], 1000);
});

test('fingerprint shims resolve without collecting browser signals', async () => {
    const p = installed('RESOURCE', ['fingerprint2.js']);
    p.run('Fingerprint2.get(values => self.components = values)');
    p.calls.timers[0][0](); assert.equal(p.run('components.length'), 0);
    assert.equal((await p.run('Fingerprint2.getPromise()')).length, 0);
    const q = installed('RESOURCE', ['fingerprint3.js']);
    const result = await q.run('FingerprintJS.load().then(instance => instance.get())');
    assert.match(result.visitorId, /^[a-f0-9]{32}$/);
    assert.equal(await q.run('FingerprintJS.load().then(instance => instance.get())')
        .then(value => value.visitorId), result.visitorId);
});

test('GPT queues run and PopAds writes abort while dummy APIs remain callable', () => {
    const p = installed('RESOURCE', ['googletagservices_gpt.js'], { before:
        'googletag={cmd:[function(){self.ready=true}]};' });
    assert.equal(p.run('ready'), true);
    p.run('googletag.cmd.push(function(){self.next=true})');
    assert.equal(p.run('next'), true);
    const q = installed('RESOURCE', ['popads.js']);
    assert.throws(() => q.run('PopAds={}'), { name: 'ReferenceError' });
    const r = installed('RESOURCE', ['popads-dummy.js']);
    assert.equal(r.run('PopAds = {advert:true}; typeof PopAds'), 'object');
    assert.equal(r.run('PopAds.advert'), undefined);
});

test('DOM hooks retain protected timers and listeners despite matching defusing rules', () => {
    const d = dom([
        ['SET_ATTRIBUTE', ['div', 'data-safe', 'true']],
        ['PREVENT_SET_TIMEOUT', ['', '0-1000']],
        ['PREVENT_ADD_EVENT_LISTENER', ['readystatechange', 'sweep']],
    ]);
    d.p.install(); d.ready(d.p, 'complete');
    const node = d.add(new d.Element()); d.mutate([node]); d.p.flushFrames();
    assert.equal(node.getAttribute('data-safe'), 'true');
    assert.equal(d.observers.length, 1);
});

test('timer and eval families install one native hook each even when BAB shares both', () => {
    const p = page([
        ['PREVENT_SET_TIMEOUT', ['advert', '']], ['ADJUST_SET_TIMEOUT', ['', '*', '0.5']],
        ['PREVENT_BAB', []], ['PREVENT_EVAL', ['advert']],
    ], { before: 'hookCounts={}; const nativeDefine=Object.defineProperty;' +
        'Object.defineProperty=function(owner,key,desc){' +
        'if(owner===self)hookCounts[key]=(hookCounts[key]||0)+1;' +
        'return nativeDefine(owner,key,desc);};' });
    p.install();
    assert.equal(p.run('hookCounts.setTimeout'), 1);
    assert.equal(p.run('hookCounts.eval'), 1);
});

test('stack matching normalizes document URLs, column numbers and injected scripts', () => {
    const before = 'Error=class{constructor(token){this.stack=token+' +
        '"\\nat helper (<anonymous>:1:2)\\nat detector (https://site.example/path:123:99)"+' +
        '"\\nat caller (<anonymous>:12:55)";}};';
    for (const pattern of ['inlineScript:123:1', 'injectedScript:12:1', 'stackDepth:2']) {
        const p = installed('ABORT_STACK_TRACE', ['detector', pattern], { before });
        assert.throws(() => p.run('detector'), { name: 'ReferenceError' });
    }
});
