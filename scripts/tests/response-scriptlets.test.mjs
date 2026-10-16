import assert from 'node:assert/strict';
import test from 'node:test';
import { page } from './scriptlet-fixture.mjs';
import { xhrFixture } from './xhr-fixture.mjs';

const jsonArgs = (paths = 'ads', props = '', needles = '', stack = '') =>
    [paths, needles, 'propsToMatch', props, 'stackToMatch', stack];
function xhrPage(rules) {
    const native = xhrFixture();
    const p = page(rules, { globals: native.globals }); p.install();
    return { ...p, ...native, flush() {
        for (const [callback] of p.calls.timers.splice(0)) { callback(); }
    } };
}
function fetchPage(rules, factory) {
    const calls = [];
    const p = page(rules, { globals: { TextDecoder,
        fetch(...args) { calls.push(args); return factory(...args); } } });
    p.install(); return { ...p, fetches: calls };
}

test('XHR prevention preserves event order, headers and brands without sending', () => {
    const p = xhrPage([['PREVENT_XHR', ['url:/ads method:GET', 'emptyObj']]]);
    const xhr = new p.XHR(); const events = [];
    for (const type of ['readystatechange', 'loadstart', 'progress', 'load', 'loadend']) {
        xhr.addEventListener(type, () => events.push([type, xhr.readyState]));
    }
    xhr.open('GET', '/ads'); xhr.setRequestHeader('x-test', 'yes'); xhr.send();
    assert.equal(p.sends.length, 0); assert.equal(xhr.readyState, 1);
    assert.equal(xhr.getResponseHeader('content-type'), null);
    assert.throws(() => xhr.send(), { name: 'InvalidStateError' });
    assert.throws(() => xhr.setRequestHeader('later', 'no'), { name: 'InvalidStateError' });
    p.flush();
    assert.equal(xhr.responseText, '{}'); assert.equal(xhr.response, '{}');
    assert.equal(xhr.status, 200); assert.equal(xhr.statusText, 'OK');
    assert.equal(xhr.responseURL, 'https://site.example/ads');
    assert.equal(xhr.getResponseHeader('CONTENT-TYPE'), 'text/plain');
    assert.equal(xhr.getResponseHeader('missing'), null);
    assert.match(xhr.getAllResponseHeaders(), /content-length: 2\r\n/);
    assert.deepEqual(events, [['readystatechange', 1], ['loadstart', 1],
        ['readystatechange', 2], ['readystatechange', 3], ['progress', 3],
        ['readystatechange', 4], ['load', 4], ['loadend', 4]]);
    assert.equal(xhr instanceof p.XHR, true);
});

test('XHR abort cancels success and supports reuse with native requests', () => {
    const p = xhrPage([['PREVENT_XHR', ['/ads', '']]]);
    const xhr = new p.XHR(); const events = [];
    for (const type of ['abort', 'load', 'loadend']) {
        xhr.addEventListener(type, () => events.push(type));
    }
    xhr.open('GET', '/ads'); xhr.send(); xhr.abort(); p.flush();
    assert.deepEqual(events, ['abort', 'loadend']);
    assert.equal(xhr.readyState, 0); assert.equal(xhr.status, 0);
    assert.equal(xhr.responseText, '');
    xhr.open('GET', '/login'); xhr.send('credentials');
    assert.equal(p.sends.length, 1);
    xhr.complete('session'); assert.equal(xhr.responseText, 'session');
    xhr.open('GET', '/ads'); xhr.send(); p.flush();
    assert.equal(xhr.responseText, ''); assert.equal(xhr.status, 200);
});

test('reentrant XHR open and abort handlers cannot publish stale completion events', () => {
    const p = xhrPage([['PREVENT_XHR', ['/ads', 'emptyObj']]]);
    const xhr = new p.XHR(); let loads = 0;
    xhr.onload = () => loads++;
    xhr.onreadystatechange = () => {
        if (xhr.readyState === 2) { xhr.open('POST', '/login'); xhr.send('token'); }
    };
    xhr.open('GET', '/ads'); xhr.send(); p.flush();
    assert.equal(loads, 0); assert.equal(p.sends.length, 1);
    xhr.onreadystatechange = null; xhr.complete('login');
    assert.equal(xhr.responseText, 'login');
    xhr.open('GET', '/ads');
    xhr.onreadystatechange = () => { if (xhr.readyState === 3) { xhr.abort(); } };
    xhr.send(); p.flush(); assert.equal(loads, 0); assert.equal(xhr.readyState, 0);
});

test('XHR supports synchronous responses and binary and JSON response contracts', () => {
    const p = xhrPage([['PREVENT_XHR', ['/ads', 'emptyArr']]]);
    const xhr = new p.XHR(); xhr.open('GET', '/ads', false); xhr.send();
    assert.equal(xhr.readyState, 4); assert.equal(xhr.responseText, '[]');
    assert.equal(p.calls.timers.length, 0);
    for (const type of ['json', 'arraybuffer', 'blob']) {
        xhr.open('GET', '/ads'); xhr.responseType = type; xhr.send(); p.flush();
        assert.throws(() => xhr.responseText, { name: 'InvalidStateError' });
        assert.throws(() => xhr.responseXML, { name: 'InvalidStateError' });
        assert.throws(() => { xhr.responseType = ''; }, { name: 'InvalidStateError' });
        if (type === 'json') { assert.equal(JSON.stringify(xhr.response), '{}'); }
        if (type === 'arraybuffer') { assert.equal(xhr.response.byteLength, 0); }
        if (type === 'blob') { assert.equal(xhr.response.size, 0); }
    }
});

test('XHR native validation and frozen instances fail open without swallowing errors', () => {
    const p = xhrPage([['PREVENT_XHR', ['/ads', '']]]); const xhr = new p.XHR();
    assert.throws(() => xhr.send(), { name: 'InvalidStateError' });
    assert.throws(() => xhr.open('GET', 'invalid'), { name: 'SyntaxError' });
    xhr.open('GET', '/ads'); Object.preventExtensions(xhr); xhr.send();
    assert.equal(p.sends.length, 1);
});

test('XHR pruning changes DONE text and JSON, guards paths and preserves identity', () => {
    const p = xhrPage([['JSON_PRUNE_XHR', jsonArgs('ads items.[].advert', '/api', 'data')]]);
    const xhr = new p.XHR(); xhr.open('GET', '/api'); xhr.send();
    xhr.complete('{"ads":1,"data":2}', { state: 3 });
    assert.equal(xhr.responseText, '{"ads":1,"data":2}');
    xhr.complete('{"ads":1,"data":2,"items":[{"advert":3,"login":4}]}');
    assert.deepEqual(JSON.parse(xhr.responseText), { data: 2, items: [{ login: 4 }] });
    assert.equal(xhr.response, xhr.responseText);
    xhr.open('GET', '/api'); xhr.responseType = 'json'; xhr.send();
    xhr.complete('{"ads":1,"data":2}', { json: true });
    assert.deepEqual(JSON.parse(JSON.stringify(xhr.response)), { data: 2 });
    assert.equal(xhr.response, xhr.response);
    assert.throws(() => xhr.responseText, { name: 'InvalidStateError' });
    xhr.open('GET', '/login'); xhr.send(); xhr.complete('{"ads":1,"data":2}');
    assert.equal(xhr.responseText, '{"ads":1,"data":2}');
    xhr.open('GET', '/api'); xhr.send(); xhr.complete('{"ads":1}');
    assert.equal(xhr.responseText, '{"ads":1}');
});

test('XHR malformed JSON and oversized bodies retain their complete input', () => {
    const p = xhrPage([['JSON_PRUNE_XHR', jsonArgs()]]);
    const xhr = new p.XHR(); xhr.open('GET', '/api'); xhr.send();
    for (const text of ['not json', '{"ads":"' + 'x'.repeat(1024 * 1024) + '"}']) {
        xhr.complete(text); assert.equal(xhr.responseText, text);
    }
});

test('Fetch pruning preserves status, metadata, headers and native readers and clones',
    async () => {
    const original = new Response('{"ads":1,"login":"token"}', {
        status: 201, statusText: 'Created',
        headers: { 'content-type': 'application/json', 'x-session': 'yes',
            'content-length': '25', 'content-encoding': 'gzip', etag: 'stale' },
    });
    Object.defineProperties(original, { url: { value: 'https://site.example/api' },
        redirected: { value: true }, type: { value: 'basic' } });
    const p = fetchPage([['JSON_PRUNE_FETCH', jsonArgs('ads', 'url:/api method:POST')]],
        () => Promise.resolve(original));
    p.context.request = new Request('https://site.example/api', { method: 'GET' });
    const response = await p.run('fetch(request, { method: "POST" })');
    assert.notEqual(response, original); assert.equal(response.status, 201);
    assert.equal(response.url, original.url); assert.equal(response.type, 'basic');
    assert.equal(response.redirected, true);
    assert.equal(response.headers.get('x-session'), 'yes');
    for (const header of ['content-length', 'content-encoding', 'etag']) {
        assert.equal(response.headers.get(header), null);
    }
    const clone = response.clone(); assert.equal(clone.url, original.url);
    assert.equal(clone.clone().type, 'basic');
    assert.deepEqual(await clone.json(), { login: 'token' });
    assert.equal(await response.text(), '{"login":"token"}');
    assert.equal(response.bodyUsed, true); assert.throws(() => response.clone(), TypeError);
    assert.equal(original.bodyUsed, false);
});

test('Fetch calls without edits retain native promise and response identity', async () => {
    const original = new Response('{"login":1}'); const promise = Promise.resolve(original);
    const p = fetchPage([['JSON_PRUNE_FETCH', jsonArgs('ads', '/api')]], () => promise);
    assert.equal(p.run('fetch("/login")'), promise);
    assert.equal(await p.run('fetch("/api")'), original);
    assert.equal(await original.text(), '{"login":1}');
});

test('Fetch failures, abort reasons and malformed or opaque responses retain native behavior',
    async () => {
        const reason = new DOMException('Cancelled', 'AbortError');
        const p = fetchPage([['JSON_PRUNE_FETCH', jsonArgs()]], () => Promise.reject(reason));
        await assert.rejects(p.run('fetch("/api")'), error => error === reason);
        for (const original of [new Response('invalid'), new Response(null, { status: 204 }),
            Response.error(), new Response('ads', {
                headers: { 'content-type': 'text/plain; charset=latin1' },
            })]) {
            const q = fetchPage([['JSON_PRUNE_FETCH', jsonArgs()]],
                () => Promise.resolve(original));
            assert.equal(await q.run('fetch("/api")'), original);
        }
    });

test('Fetch body limits retain the whole original body for native consumers', async () => {
    const text = '{"ads":"' + 'x'.repeat(1024 * 1024) + '"}';
    const original = new Response(text);
    const p = fetchPage([['JSON_PRUNE_FETCH', jsonArgs()]], () => Promise.resolve(original));
    const result = await p.run('fetch("/api")'); assert.equal(result, original);
    assert.equal(await result.text(), text);
});

test('trusted text replacement composes with pruning without evaluating replacement code',
    async () => {
    const p = fetchPage([
        ['JSON_PRUNE_FETCH', jsonArgs()],
        ['TRUSTED_REPLACE_FETCH', ['token', 'alert(1)', '/api']],
    ], () => Promise.resolve(new Response('{"ads":1,"login":"token"}')));
    assert.deepEqual(await (await p.run('fetch("/api")')).json(), { login: 'alert(1)' });
    assert.equal(p.run('typeof alert'), 'undefined');
    const q = xhrPage([['TRUSTED_REPLACE_XHR', ['/advert/i', 'removed', '/api']]]);
    const xhr = new q.XHR(); xhr.open('GET', '/api'); xhr.send(); xhr.complete('Advert login');
    assert.equal(xhr.responseText, 'removed login');
});

test('HLS pruning removes matching segments and retains unrelated media and tags', async () => {
    const input = '#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nadvert.ts\n' +
        '#EXT-X-DISCONTINUITY\n#EXTINF:6,\nvideo.ts\n#EXT-X-ENDLIST\n';
    const expected = '#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nvideo.ts\n#EXT-X-ENDLIST\n';
    const p = fetchPage([['M3U_PRUNE', ['advert', '.m3u8']]],
        () => Promise.resolve(new Response(input)));
    assert.equal(await (await p.run('fetch("/video.m3u8")')).text(), expected);
    assert.equal(await (await p.run('fetch("/login")')).text(), input);
    const q = xhrPage([['M3U_PRUNE', ['advert', '.m3u8']]]);
    const xhr = new q.XHR(); xhr.open('GET', '/video.m3u8'); xhr.send(); xhr.complete(input);
    assert.equal(xhr.responseText, expected);
    xhr.open('GET', '/video.m3u8'); xhr.send(); xhr.complete('advert.ts');
    assert.equal(xhr.responseText, 'advert.ts');
});

test('stack-scoped pruning and mixed prevention retain independent native hooks', async () => {
    const p = fetchPage([['JSON_PRUNE_FETCH', jsonArgs('ads', '', '', 'targetCall')]],
        () => Promise.resolve(new Response('{"ads":1,"login":2}')));
    const first = await p.run('function targetCall(){ return fetch("/api"); } targetCall()');
    assert.deepEqual(await first.json(), { login: 2 });
    assert.deepEqual(await (await p.run('fetch("/api")')).json(), { ads: 1, login: 2 });
    const q = page([['PREVENT_FETCH', ['/safe-ads', '']],
        ['TRUSTED_PREVENT_FETCH', ['/trusted-ads', 'literal text']]]);
    q.install();
    assert.equal(await (await q.run('fetch("/trusted-ads")')).text(), 'literal text');
    assert.equal(await (await q.run('fetch("/safe-ads")')).text(), '');
});

test('trusted JSON constants replace values and stay locked across type-changing writes', () => {
    const p = page([
        ['TRUSTED_SET', ['config', 'json:{"enabled":true,"name":"custom value"}']],
        ['TRUSTED_SET', ['literal', '{"value":"custom string"}']],
    ], { before: 'config=42; literal=false;' });
    p.install();
    assert.equal(p.run('config.enabled'), true); assert.equal(p.run('config.name'), 'custom value');
    p.run('config=false; literal=42;');
    assert.equal(p.run('config.enabled'), true); assert.equal(p.run('literal'), 'custom string');
    assert.equal(p.run('typeof alert'), 'undefined');
});
