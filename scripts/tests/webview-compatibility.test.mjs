import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { test } from 'node:test';
import { page } from './scriptlet-fixture.mjs';
import { xhrFixture } from './xhr-fixture.mjs';
import { routingPayload } from './scriptlet-routing.mjs';

const step = (keys, recursive = false) => [recursive, keys, null];
const editArgs = (steps, props = '', v2 = false, extract = '') =>
    ['', JSON.stringify([v2, steps]), props, '', extract];
const pruneArgs = paths => [paths, '', 'propsToMatch', '', 'stackToMatch', ''];

test('routing pools identical instructions and applies their shared exception IDs', () => {
    const rules = [
        ['SET', ['marker', 'false'], 'site.example'],
        ['SET', ['marker', 'false'], 'sub.site.example'],
        ['SET', ['marker', 'false'], 'safe.site.example', [], true],
    ];
    const [, , pool] = routingPayload(rules);
    assert.equal(pool.trim().split('\n').length, 1);
    for (const [host, expected] of [['sub.site.example', 'boolean'],
        ['safe.site.example', 'undefined']]) {
        const p = page(rules, { host }); p.install();
        assert.equal(p.run('typeof marker'), expected);
    }
});

test('property-only frames do not initialize optional runtime families', () => {
    const p = page([['SET', ['marker', 'false']]], { before: `
        Object.defineProperty(self, 'getComputedStyle', {
            get() { throw new Error('unneeded extra runtime'); }
        });
        Object.defineProperty(self, 'DOMParser', {
            get() { throw new Error('unneeded response runtime'); }
        });
    ` });
    p.install();
    assert.equal(p.run('marker'), false);
});

test('listener options use readiness events and native element matching without scans', () => {
    const args = ['click', 'advert', 'runAt', 'complete', 'elements', '.ad', 'protect', '1'];
    const p = page([['PREVENT_ADD_EVENT_LISTENER', args]], { before: `
        self.Element = class extends EventTarget {
            constructor(name) { super(); this.name = name; }
            matches(selector) { return this.name === selector; }
        };
        document.querySelectorAll = () => { throw new Error('must not scan'); };
        ad = new Element('.ad'); login = new Element('.login');
    ` });
    p.install();
    const clicks = () => p.calls.listeners.filter(row => row[1] === 'click').length;
    p.run('ad.addEventListener("click", function advert() {})');
    assert.equal(clicks(), 1);
    p.document.readyState = 'interactive'; p.emit('readystatechange', {}, p.document);
    p.run('ad.addEventListener("click", function advert() {})');
    assert.equal(clicks(), 2);
    p.document.readyState = 'complete'; p.emit('readystatechange', {}, p.document);
    const hook = p.run('EventTarget.prototype.addEventListener');
    p.run('EventTarget.prototype.addEventListener = function replacement() {}');
    assert.equal(p.run('EventTarget.prototype.addEventListener'), hook);
    p.run('ad.addEventListener("click", function advert() {})');
    assert.equal(clicks(), 2);
    p.run('ad.addEventListener("click", function loginCallback() {})');
    p.run('login.addEventListener("click", function advert() {})');
    p.run('ad.addEventListener("dblclick", function advert() {})');
    assert.equal(clicks(), 4);
    assert.equal(p.calls.listeners.filter(row => row[1] === 'dblclick').length, 1);
});

test('listener window/document options also cover native own methods', () => {
    for (const owner of ['window', 'document']) {
        const p = page([['PREVENT_ADD_EVENT_LISTENER',
            ['click', '', 'elements', owner]]]); p.install();
        p.run('window.addEventListener("click", function advert() {})');
        p.run('document.addEventListener("click", function advert() {})');
        assert.equal(p.calls.listeners.filter(row => row[1] === 'click').length, 1, owner);
    }
});

test('matching frames parse only their bucket and records in a large subscription', () => {
    const rules = Array.from({ length: 17000 }, (_, index) =>
        ['SET', ['marker', 'false'], 'site' + index + '.example']);
    for (const [host, parsed] of [['site16999.example', 3], ['unrelated.example', 0]]) {
        const p = page(rules, { host, before: `
            parseCalls = 0;
            const parse = JSON.parse;
            JSON.parse = function(...args) {
                parseCalls++;
                return Reflect.apply(parse, this, args);
            };
        ` });
        p.install();
        assert.equal(p.run('parseCalls'), parsed, host);
        assert.equal(p.run('typeof marker'), parsed ? 'boolean' : 'undefined');
    }
});

test('JSON editing preserves array deletion, guards, and native reviver errors', () => {
    const program = [step(['items']), [false, [], [false, [step(['advert'])], '', null]]];
    const p = page([['JSON_EDIT', editArgs(program)]]); p.install();
    assert.equal(p.run(`JSON.stringify(JSON.parse('{
        "items":[{"advert":true,"id":1},{"id":2},{"advert":false,"id":3}]
    }'))`.replace(/\n/g, '')), '{"items":[{"id":2}]}');
    assert.throws(() => p.run('JSON.parse("broken")'), { name: 'SyntaxError' });
    assert.throws(() => p.run('JSON.parse("{}", () => { throw new TypeError("reviver"); })'),
        { name: 'TypeError', message: 'reviver' });
});

test('JSON edit completes traversal before mutation and avoids invoking getters', () => {
    const p = page([['JSON_EDIT', editArgs([step(['ads'], true)])]]); p.install();
    assert.equal(p.run(`(() => {
        let value = { ads: 1 }, cursor = value;
        for (let i = 0; i < 80; i++) { cursor.child = {}; cursor = cursor.child; }
        const result = JSON.parse('{}', () => value);
        return result.ads;
    })()`), 1);
    assert.equal(p.run(`(() => {
        const value = {};
        Object.defineProperty(value, 'ads', { get() { throw new Error('getter'); } });
        return JSON.parse('{}', () => value) === value;
    })()`), true);
});

test('regex-selected array keys retain upstream sparse-array behavior', () => {
    const program = [step(['numbers']), step([{ regex: '^[12]$', flags: '' }])];
    const p = page([['JSON_EDIT', editArgs(program)]]); p.install();
    assert.equal(p.run(`(() => {
        const value = JSON.parse('{"numbers":[1,2,3]}').numbers;
        return [value.length, 0 in value, 1 in value, 2 in value].join(',');
    })()`), '3,true,false,false');
});

test('all applicable response operations beyond the old 32-rule limit execute', () => {
    const native = xhrFixture();
    const rules = Array.from({ length: 40 }, (_, i) =>
        ['JSON_PRUNE_XHR', pruneArgs('ads' + i)]);
    const p = page(rules, { globals: native.globals }); p.install();
    const xhr = new native.XHR(); xhr.open('GET', '/api'); xhr.send();
    xhr.complete(JSON.stringify(Object.fromEntries(rules.map((_, i) => ['ads' + i, i]))));
    assert.equal(xhr.responseText, '{}');
});

test('JSON/JSONL Fetch editors preserve other calls, separators, and metadata', async () => {
    for (const [type, input, expected] of [
        ['JSON_EDIT_FETCH', '{"ads":1,"keep":2}', '{"keep":2}'],
        ['JSONL_EDIT_FETCH', '{"ads":1,"keep":2}\r\ninvalid\r\n{"ads":3}\r\n',
            '{"keep":2}\r\ninvalid\r\n{}\r\n'],
    ]) {
        const calls = [];
        const p = page([[type, editArgs([step(['ads'], true)], '/api')]], {
            globals: { TextDecoder, fetch(url) {
                calls.push(url);
                const response = new Response(input, { headers: { 'x-session': 'keep' } });
                Object.defineProperty(response, 'url', { value: url });
                return Promise.resolve(response);
            } },
        });
        p.install();
        const response = await p.run('fetch("https://site.example/api")');
        assert.equal(response.url, 'https://site.example/api');
        assert.equal(response.headers.get('x-session'), 'keep');
        const clone = response.clone();
        assert.equal(await response.text(), expected); assert.equal(await clone.text(), expected);
        assert.equal(await (await p.run('fetch("https://site.example/login")')).text(), input);
        assert.equal(calls.length, 2);
    }
});

test('JSONL extraction edits only its captured JSON and preserves the rest of each line', () => {
    const native = xhrFixture();
    const p = page([['JSONL_EDIT_XHR', editArgs([step(['ads'], true)], '', false,
        '^data: (.*)$')]], { globals: native.globals }); p.install();
    const xhr = new native.XHR(); xhr.open('GET', '/api'); xhr.send();
    xhr.complete('event: update\ndata: {"ads":1,"keep":2}\n\n');
    assert.equal(xhr.responseText, 'event: update\ndata: {"keep":2}\n\n');
});

test('XHR JSON editors preserve JSON response brands and text-reader contracts', () => {
    const native = xhrFixture();
    const p = page([['JSON_EDIT_XHR', editArgs([step(['ads'], true)], '/api')]],
        { globals: native.globals }); p.install();
    const xhr = new native.XHR(); xhr.open('GET', '/api'); xhr.responseType = 'json'; xhr.send();
    xhr.complete('{"ads":1,"keep":2}', { json: true });
    assert.deepEqual(JSON.parse(JSON.stringify(xhr.response)), { keep: 2 });
    assert.equal(xhr.response, xhr.response);
    assert.throws(() => xhr.responseText, { name: 'InvalidStateError' });
});

const output = process.env.SCRIPTLET_OUTPUT_DIR;
test('Kotlin-produced extended routes respect actual frame URLs and ancestor origins',
    { skip: !output }, () => {
        const source = readFileSync(join(output, 'extended-injection.js'), 'utf8');
        for (const [host, path, ancestors, markers] of [
            ['site.example', '/path?q=1', [], ['pathMarker', 'mixedMarker']],
            ['site.example', '/path/safe', [], ['mixedMarker']],
            ['www.shop.co.uk', '/checkout', [], ['entityMarker']],
            ['img123.example', '/', [], ['regexMarker']],
            ['img1234.example', '/', [], []],
            ['site.example', '/promo7', [], ['urlMarker', 'mixedMarker']],
            ['child.example', '/', ['https://parent.example'], ['frameMarker']],
            ['child.example', '/', [], []], ['safe.site.example', '/', [], []],
        ]) {
            const p = page([], { host, globals: { location: { hostname: host, protocol: 'https:',
                href: 'https://' + host + ':8443' + path,
                ancestorOrigins: { length: ancestors.length, item: i => ancestors[i] } } } });
            p.run(source);
            for (const name of ['pathMarker', 'mixedMarker', 'entityMarker', 'regexMarker',
                'urlMarker', 'frameMarker']) {
                assert.equal(p.run(`typeof ${name} === 'boolean'`), markers.includes(name),
                    host + path + ' ' + name);
            }
        }
    });

const input = { ads: 1, 'ad-key': 4, numbers: [1, 2, 3], items: [
    { ad: 1, advert: true, kind: 'ad', value: 4 }, { id: 2, content: true },
    { id: 3, advert: false, kind: 'content', value: 2 },
], nested: { ads: 9 } };
test('Kotlin-compiled JSONPath programs have the expected mutation semantics',
    { skip: !output }, () => {
        const cases = JSON.parse(readFileSync(join(output, 'json-edit-cases.json'), 'utf8'));
        const expected = {
            '..ads': value => { delete value.ads; delete value.nested.ads; },
            '$.ads': value => { delete value.ads; },
            '$.items[*].ad': value => { delete value.items[0].ad; },
            '$.items[-1]': value => { value.items.pop(); },
            '$.items[?@.advert]': value => { value.items = [value.items[1]]; },
            'v2:$.items[?@.kind=="ad"]': value => { value.items.shift(); },
            '$.items[0,2]': value => { value.items = [value.items[1]]; },
            '$.items[?@.value>=3]': value => { value.items.shift(); },
            '$.items[?!@.content]': value => { value.items = [value.items[1]]; },
            "$['ad-key']": value => { delete value['ad-key']; },
            'v2:$.items[*]{3};$..ads': value => { delete value.ads; delete value.nested.ads; },
            'v2:$.missing{0};$..ads': value => { delete value.ads; delete value.nested.ads; },
            'v2:$.missing;$..ads': () => {},
            'v2:$.items[*]{2,4};$.ads': value => { delete value.ads; },
            'v2:$.items[*]{,2};$.ads': () => {},
            'v2:$.items[*]{3,};$.missing{0};$.ads': value => { delete value.ads; },
            'v2:$../^ad/': value => {
                delete value.ads; delete value['ad-key']; delete value.nested.ads;
                for (const item of value.items) { delete item.ad; delete item.advert; }
            },
            'v2:$.items[?@.kind=/^AD$/i]': value => { value.items.shift(); },
            'v2:$.numbers[?@>=2]': value => { value.numbers = [1]; },
            'v2:$.items[?@.kind=/^ad$/];$.ads': value => { delete value.ads; },
            '$.numbers./^[12]$/': value => { value.numbers = [1, null, null]; },
        };
        for (const [query, source] of cases) {
            const p = page([]); p.run(source);
            const encoded = JSON.stringify(JSON.stringify(input));
            const after = JSON.parse(p.run(`JSON.stringify(JSON.parse(${encoded}))`));
            const wanted = structuredClone(input); expected[query](wanted);
            assert.deepEqual(after, wanted, query);
        }
    });

test('optional upstream differential check compares the same compiled queries',
    { skip: !output || !process.env.UBLOCK_JSONPATH_SOURCE }, async () => {
        const code = readFileSync(process.env.UBLOCK_JSONPATH_SOURCE, 'utf8');
        const { JSONPath } = await import('data:text/javascript,' + encodeURIComponent(code));
        const cases = JSON.parse(readFileSync(join(output, 'json-edit-cases.json'), 'utf8'));
        for (const [query, source] of cases) {
            const program = JSONPath.create(query);
            assert.equal(program.valid, true, query);
            const before = structuredClone(input);
            const wanted = program.apply(before) ?? before;
            const p = page([]); p.run(source);
            const encoded = JSON.stringify(JSON.stringify(input));
            const after = JSON.parse(p.run(`JSON.stringify(JSON.parse(${encoded}))`));
            assert.deepEqual(after, JSON.parse(JSON.stringify(wanted)), query);
        }
    });

test('new redirect-only shims provide fixed APIs without making network calls', () => {
    const p = page([]);
    for (const name of ['google-analytics_cx_api.js', 'google-analytics_inpage_linkid.js',
        'scorecardresearch_beacon.js']) {
        p.run(readFileSync(new URL('../../adblock/src/main/resources/adblock/ubo/' + name,
            import.meta.url), 'utf8'));
    }
    assert.equal(p.run('cxApi.chooseVariation()'), 0);
    assert.equal(p.run('typeof _gaq.push'), 'function');
    assert.equal(p.run('typeof COMSCORE.beacon'), 'function');
    assert.equal(p.run('COMSCORE.purge(); JSON.stringify(_comscore)'), '[]');
    assert.equal(p.calls.fetch.length, 0);
});

test('IMA shim delivers lifecycle callbacks through captured animation frames', () => {
    const p = page([
        ['PREVENT_ANIMATION_FRAME', ['/./']], ['RESOURCE', ['google-ima.js']],
    ], { before: `
        document.createElement = () => ({ style: { setProperty() {} } });
    ` });
    p.install();
    p.run(`
        events = []; userContext = { content: 'video' };
        const ima = google.ima;
        display = new ima.AdDisplayContainer({ appendChild() {} });
        loader = new ima.AdsLoader(display);
        loader.addEventListener(ima.AdsManagerLoadedEvent.Type.ADS_MANAGER_LOADED, event => {
            events.push('manager');
            if (event.getUserRequestContext() !== userContext) { throw new Error('context'); }
            manager = event.getAdsManager({}, new ima.AdsRenderingSettings());
            manager.setVolume(0.25);
            manager.addEventListener(ima.AdEvent.Type.ALL_ADS_COMPLETED, () => events.push('done'));
            manager.addEventListener(ima.AdEvent.Type.CONTENT_RESUME_REQUESTED,
                () => events.push('resume'));
            manager.init(640, 360, ima.ViewMode.NORMAL); manager.start();
        });
        loader.addEventListener(ima.AdErrorEvent.Type.AD_ERROR,
            event => events.push(event.getError().getInnerError() === null ? 'error' : 'bad'));
        loader.requestAds(new ima.AdsRequest(), userContext);
    `);
    p.flushFrames(); p.flushFrames();
    assert.equal(p.run('JSON.stringify(events)'), '["manager","error","done","resume"]');
    assert.equal(p.run('manager.getVolume()'), 0.25);
    assert.equal(p.calls.fetch.length, 0);
    assert.equal(p.calls.frames.size, 0);
    const existing = page([['RESOURCE', ['google-ima.js']]], {
        before: 'google = { ima: { VERSION: "native", marker: true } };',
    });
    existing.install();
    assert.equal(existing.run('google.ima.marker'), true);
});

test('Piano redirect exposes its inert analytics API', () => {
    const p = page([]);
    p.run(readFileSync(new URL('../../adblock/src/main/resources/adblock/ubo/piano-analytics.js',
        import.meta.url), 'utf8'));
    assert.equal(p.run('typeof pa.getVisitorId'), 'function');
    assert.equal(p.run('pa.sendEvent("page.display", { url: location.href })'), undefined);
    assert.equal(p.calls.fetch.length, 0);
});

test('missing regex capture indices leave JSONL intact and keep other scriptlets working', () => {
    const native = xhrFixture();
    const p = page([
        ['JSONL_EDIT_XHR', editArgs([step(['ads'])], '', false, '^data: (.*)$')],
        ['SET', ['marker', 'false']],
    ], { globals: native.globals, before: `
        const NativeRegex = RegExp;
        RegExp = function(pattern, flags) {
            if (flags === 'd') { throw new Error('No capture indices'); }
            return new NativeRegex(pattern, flags);
        };
        RegExp.prototype = NativeRegex.prototype;
    ` });
    assert.doesNotThrow(() => p.install());
    assert.equal(p.run('marker'), false);
    const xhr = new native.XHR(); xhr.open('GET', '/api'); xhr.send();
    xhr.complete('data: {"ads":1,"keep":2}\n');
    assert.equal(xhr.responseText, 'data: {"ads":1,"keep":2}\n');
});


test('legacy and v2 object filters preserve upstream object-filter selection semantics', () => {
    const filter = [false, [], [false, [step(['ads'])], '', null]];
    for (const version2 of [false, true]) {
        const p = page([['JSON_EDIT', editArgs([step(['nested']), filter], '', version2)]]);
        p.install();
        const result = p.run('JSON.stringify(JSON.parse(\'{"nested":{"ads":1},"keep":2}\'))');
        const expected = version2 ? { nested: { ads: 1 }, keep: 2 } : { keep: 2 };
        assert.deepEqual(JSON.parse(result), expected);
    }
});
