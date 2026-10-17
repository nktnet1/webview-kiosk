import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { test } from 'node:test';
import { page } from './scriptlet-fixture.mjs';
import { xhrFixture } from './xhr-fixture.mjs';

const step = (keys, recursive = false) => [recursive, keys, null];
const editArgs = (steps, props = '', v2 = false, extract = '') =>
    ['', JSON.stringify([v2, steps]), props, '', extract];
const pruneArgs = paths => [paths, '', 'propsToMatch', '', 'stackToMatch', ''];

test('matching frames parse only their bucket and records in a large subscription', () => {
    const rules = Array.from({ length: 17000 }, (_, index) =>
        ['SET', ['marker', 'false'], 'site' + index + '.example']);
    for (const [host, parsed] of [['site16999.example', 2], ['unrelated.example', 0]]) {
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

const input = { ads: 1, 'ad-key': 4, items: [
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
            assert.deepEqual(after, wanted, query);
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
