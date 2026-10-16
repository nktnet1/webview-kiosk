import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createRequire } from 'node:module';
import { before, after, test } from 'node:test';
import { page as fixture } from './scriptlet-fixture.mjs';

// Explicit opt-in: ordinary offline checks never install or require a browser.
const enabled = Boolean(process.env.SCRIPTLET_CHROMIUM);
const options = { skip: !enabled };
let browser; let server; let base;
const requests = [];
const jsonArgs = (paths = 'ads', props = '') =>
    [paths, '', 'propsToMatch', props, 'stackToMatch', ''];
before(async () => {
    if (!enabled) { return; }
    const { chromium } = createRequire(import.meta.url)('playwright');
    browser = await chromium.launch({ executablePath: process.env.SCRIPTLET_CHROMIUM,
        headless: true, args: ['--no-sandbox'] });
    server = createServer((req, res) => {
        requests.push(req.url);
        const send = (type, text, status = 200) => {
            res.writeHead(status, { 'Content-Type': type, 'x-session': 'retained' }); res.end(text);
        };
        if (req.url === '/') {
            return send('text/html', '<!doctype html><script>authorProbe=window.probe;</script>' +
                '<body><main id="login">Log in</main></body>');
        }
        if (req.url === '/manifest.xml') {
            return send('text/xml',
                '<MPD><Period><Ad id="ads"/><Content id="video"/></Period></MPD>');
        }
        if (req.url === '/malformed.xml') { return send('text/xml', '<MPD><Ad></MPD>'); }
        if (req.url === '/doctype.xml') {
            return send('text/xml', '<!DOCTYPE root [<!ENTITY x "value">]><root><Ad/></root>');
        }
        if (req.url === '/video.m3u8') {
            return send('application/vnd.apple.mpegurl', '#EXTM3U\n#EXTINF:6,\nadvert.ts\n' +
                '#EXT-X-DISCONTINUITY\n#EXTINF:6,\nvideo.ts\n#EXT-X-ENDLIST\n');
        }
        if (req.url === '/edit') { return send('text/plain', 'advert token'); }
        if (req.url === '/large') {
            return send('application/json', '{"ads":"' + 'x'.repeat(1024 * 1024) + '"}');
        }
        if (req.url === '/slow') {
            const timer = setTimeout(() => send('application/json', '{"ads":1}'), 200);
            res.on('close', () => clearTimeout(timer)); return;
        }
        if (req.url === '/stream') {
            res.writeHead(200, { 'Content-Type': 'application/json' }); res.write('{"ads":1');
            const timer = setTimeout(() => res.end('}'), 5000);
            res.on('close', () => clearTimeout(timer)); return;
        }
        send('application/json', '{"ads":1,"data":{"token":"retained"},' +
            '"items":[{"advert":true,"video":true}]}');
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    base = 'http://127.0.0.1:' + server.address().port;
});
after(async () => {
    await browser?.close();
    if (server) { await new Promise(resolve => server.close(resolve)); }
});
async function open(rules) {
    const context = await browser.newContext();
    const source = fixture(rules.map(([type, args]) => [type, args, '127.0.0.1']),
        { host: '127.0.0.1' }).source;
    await context.addInitScript({ content: source });
    const page = await context.newPage();
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    await page.goto(base); return { context, page, errors };
}

test('Chromium installs before author scripts and prunes native Fetch readers and clones', options,
    async () => {
        const { context, page, errors } = await open([
            ['SET', ['probe', 'false']],
            ['JSON_PRUNE_FETCH', jsonArgs('ads items.[].advert', '/api')],
        ]);
        try {
            const result = await page.evaluate(async () => {
                const response = await fetch('/api.json'); const clone = response.clone();
                return { probe: authorProbe, url: response.url, cloneURL: clone.url,
                    data: await response.json(), text: await clone.text(),
                    header: response.headers.get('x-session') };
            });
            assert.equal(result.probe, false);
            assert.deepEqual(result.data, { data: { token: 'retained' },
                items: [{ video: true }] });
            assert.deepEqual(JSON.parse(result.text), result.data);
            assert.equal(result.url, base + '/api.json'); assert.equal(result.cloneURL, result.url);
            assert.equal(result.header, 'retained'); assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium native XHR prevention, abort and reuse send no blocked network request', options,
    async () => {
        const { context, page, errors } = await open([['PREVENT_XHR', ['/blocked', 'emptyObj']]]);
        try {
            const before = requests.filter(url => url === '/blocked').length;
            const result = await page.evaluate(async () => {
                const xhr = new XMLHttpRequest(); const events = [];
                xhr.onreadystatechange = () => events.push(xhr.readyState);
                xhr.open('GET', '/blocked');
                const done = new Promise(resolve => { xhr.onloadend = resolve; });
                xhr.send(); await done;
                const synthetic = { status: xhr.status, text: xhr.responseText,
                    url: xhr.responseURL, header: xhr.getResponseHeader('Content-Type') };
                xhr.open('GET', '/blocked');
                const aborted = new Promise(resolve => { xhr.onabort = () => resolve(true); });
                xhr.send(); xhr.abort(); const abort = await aborted;
                xhr.open('GET', '/api.json');
                const native = new Promise(resolve => {
                    xhr.onload = () => resolve(JSON.parse(xhr.responseText));
                });
                xhr.send(); return { synthetic, abort, native: await native, events };
            });
            assert.equal(result.synthetic.status, 200); assert.equal(result.synthetic.text, '{}');
            assert.equal(result.synthetic.url, base + '/blocked');
            assert.equal(result.abort, true); assert.equal(result.native.ads, 1);
            assert.deepEqual(result.events.slice(0, 4), [1, 2, 3, 4]);
            assert.equal(requests.filter(url => url === '/blocked').length, before);
            assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium XML pruning preserves responseXML, documents and malformed inputs',
    options,
    async () => {
    const { context, page, errors } = await open([['XML_PRUNE', ['Ad', '', '.xml']]]);
    try {
        const result = await page.evaluate(async () => {
            const text = await (await fetch('/manifest.xml')).text();
            const malformed = await (await fetch('/malformed.xml')).text();
            const doctype = await (await fetch('/doctype.xml')).text();
            const get = type => new Promise(resolve => {
                const xhr = new XMLHttpRequest(); xhr.open('GET', '/manifest.xml');
                xhr.responseType = type;
                xhr.onload = () => resolve({ xml: xhr.responseXML.querySelectorAll('Ad').length,
                    content: xhr.responseXML.querySelectorAll('Content').length,
                    body: type === 'document' ? xhr.response.querySelectorAll('Ad').length
                        : new DOMParser().parseFromString(xhr.responseText, 'text/xml')
                            .querySelectorAll('Ad').length });
                xhr.send();
            });
            return { text, malformed, doctype, textXHR: await get(''),
                docXHR: await get('document') };
        });
        assert.equal(result.text.includes('<Ad'), false); assert.match(result.text, /Content/);
        assert.equal(result.malformed, '<MPD><Ad></MPD>');
        assert.match(result.doctype, /<!DOCTYPE/);
        assert.deepEqual(result.textXHR, { xml: 0, content: 1, body: 0 });
        assert.deepEqual(result.docXHR, { xml: 0, content: 1, body: 0 });
        assert.deepEqual(errors, []);
    } finally { await context.close(); }
});

test('Chromium native XHR JSON and binary IDL contracts survive interception',
    options,
    async () => {
    const { context, page, errors } = await open([
        ['JSON_PRUNE_XHR', jsonArgs('ads', '/api')], ['PREVENT_XHR', ['/blocked', '']],
    ]);
    try {
        const result = await page.evaluate(async () => {
            const get = (url, type) => new Promise(resolve => {
                const xhr = new XMLHttpRequest(); xhr.open('GET', url); xhr.responseType = type;
                xhr.onload = () => {
                    let error;
                    try { void xhr.responseText; } catch (e) { error = e.name; }
                    resolve({ value: type === 'arraybuffer' ? xhr.response.byteLength
                        : type === 'blob' ? xhr.response.size : xhr.response, error });
                };
                xhr.send();
            });
            return { json: await get('/api.json', 'json'),
                binary: await get('/blocked', 'arraybuffer'), blob: await get('/blocked', 'blob') };
        });
        assert.equal(result.json.value.ads, undefined);
        assert.equal(result.json.value.data.token, 'retained');
        assert.equal(result.json.error, 'InvalidStateError');
        assert.deepEqual(result.binary, { value: 0, error: 'InvalidStateError' });
        assert.deepEqual(result.blob, { value: 0, error: 'InvalidStateError' });
        assert.deepEqual(errors, []);
    } finally { await context.close(); }
});

test('Chromium synchronous prevention matches native event and readyState sequencing', options,
    async () => {
        const { context, page } = await open([['PREVENT_XHR', ['/blocked', 'emptyObj']]]);
        try {
            const result = await page.evaluate(() => {
                const get = url => {
                    const xhr = new XMLHttpRequest(); const events = [];
                    for (const name of ['readystatechange', 'loadstart', 'progress',
                        'load', 'loadend']) {
                        xhr.addEventListener(name, () => events.push([name, xhr.readyState]));
                    }
                    xhr.open('GET', url, false); xhr.send(); return events;
                };
                return { native: get('/api.json'), prevented: get('/blocked') };
            });
            assert.deepEqual(result.prevented, result.native);
        } finally { await context.close(); }
    });

test('Chromium HLS pruning and trusted text replacement preserve other content',
    options,
    async () => {
    const { context, page, errors } = await open([
        ['M3U_PRUNE', ['advert', '.m3u8']],
        ['TRUSTED_REPLACE_FETCH', ['advert', 'removed', '/edit']],
        ['TRUSTED_REPLACE_XHR', ['advert', 'removed', '/edit']],
    ]);
    try {
        const result = await page.evaluate(async () => ({
            playlist: await (await fetch('/video.m3u8')).text(),
            fetchText: await (await fetch('/edit')).text(),
            xhr: await new Promise(resolve => {
                const xhr = new XMLHttpRequest(); xhr.open('GET', '/edit');
                xhr.onload = () => resolve(xhr.responseText); xhr.send();
            }),
        }));
        assert.equal(result.playlist.includes('advert.ts'), false);
        assert.match(result.playlist, /#EXTINF:6,\nvideo.ts/);
        assert.equal(result.fetchText, 'removed token'); assert.equal(result.xhr, 'removed token');
        assert.deepEqual(errors, []);
    } finally { await context.close(); }
});

test('Chromium Fetch aborts and large bodies retain native consumer behavior',
    options,
    async () => {
    const { context, page, errors } = await open([['JSON_PRUNE_FETCH', jsonArgs()]]);
    try {
        const result = await page.evaluate(async () => {
            const controller = new AbortController();
            const pending = fetch('/slow', { signal: controller.signal }); controller.abort();
            let abort;
            try { await pending; } catch (error) { abort = error.name; }
            const large = await (await fetch('/large')).json();
            return { abort, length: large.ads.length };
        });
        assert.equal(result.abort, 'AbortError'); assert.equal(result.length, 1024 * 1024);
        assert.deepEqual(errors, []);
    } finally { await context.close(); }
});

test('Chromium overlay removal shares mutation batching and retains normal content', options,
    async () => {
        const { context, page, errors } = await open([
            ['OVERLAY_BUSTER', []], ['SET_ATTRIBUTE', ['main', 'data-tested', 'true']],
        ]);
        try {
            await page.evaluate(() => {
                const overlay = document.createElement('div'); overlay.id = 'overlay';
                overlay.style.cssText = 'position:fixed;inset:0;z-index:2000;background:white';
                document.body.append(overlay);
            });
            await page.waitForFunction(() => !document.querySelector('#overlay'));
            assert.equal(await page.locator('#login').textContent(), 'Log in');
            assert.equal(await page.locator('main').getAttribute('data-tested'), 'true');
            assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium stalled streams fail open within the inspection deadline', options, async () => {
    const { context, page, errors } = await open([['JSON_PRUNE_FETCH', jsonArgs()]]);
    try {
        const result = await page.evaluate(async () => {
            const start = performance.now(); const response = await fetch('/stream');
            const elapsed = performance.now() - start; const used = response.bodyUsed;
            await response.body.cancel(); return { elapsed, used };
        });
        assert.ok(result.elapsed >= 1000 && result.elapsed < 4500, String(result.elapsed));
        assert.equal(result.used, false); assert.deepEqual(errors, []);
    } finally { await context.close(); }
});

test('Chromium trusted data operations preserve storage, cookies and native requests', options,
    async () => {
        const { context, page, errors } = await open([
            ['TRUSTED_LOCAL_STORAGE', ['custom', 'arbitrary text']],
            ['TRUSTED_SESSION_STORAGE', ['custom', 'other text']],
            ['TRUSTED_SET_COOKIE', ['custom', 'cookie text', '/']],
            ['TRUSTED_SET', ['config', 'json:{"enabled":true,"label":"custom text"}']],
            ['TRUSTED_SET_ATTRIBUTE', ['main', 'data-literal', '[literal value]']],
            ['TRUSTED_PREVENT_XHR', ['/blocked', 'literal body']],
            ['TRUSTED_PREVENT_FETCH', ['/blocked', 'literal body']],
        ]);
        try {
            const result = await page.evaluate(async () => ({
                local: localStorage.getItem('custom'), session: sessionStorage.getItem('custom'),
                cookie: decodeURIComponent(document.cookie), config,
                attribute: document.querySelector('main').getAttribute('data-literal'),
                body: await (await fetch('/blocked')).text(),
                xhr: await new Promise(resolve => {
                    const xhr = new XMLHttpRequest(); xhr.open('GET', '/blocked');
                    xhr.onload = () => resolve(xhr.responseText); xhr.send();
                }),
                native: await (await fetch('/api.json')).json(),
            }));
            assert.equal(result.local, 'arbitrary text');
            assert.equal(result.session, 'other text');
            assert.match(result.cookie, /custom=cookie text/);
            assert.deepEqual(result.config, { enabled: true, label: 'custom text' });
            assert.equal(result.attribute, '[literal value]');
            assert.equal(result.body, 'literal body'); assert.equal(result.xhr, 'literal body');
            assert.equal(result.native.data.token, 'retained'); assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium Navigation API cancels automatic matches and permits user navigation', options,
    async t => {
        const { context, page, errors } = await open([
            ['PREVENT_NAVIGATION', ['/blocked-navigation']],
        ]);
        try {
            if (!await page.evaluate(() => Boolean(window.navigation))) {
                t.skip('Native Navigation API is unavailable'); return;
            }
            const result = await page.evaluate(async () => {
                const before = location.href;
                const transition = navigation.navigate('/blocked-navigation');
                const outcomes = await Promise.allSettled([
                    transition.committed, transition.finished,
                ]);
                const anchor = document.createElement('a');
                anchor.href = '/blocked-navigation'; anchor.textContent = 'User navigation';
                document.body.append(anchor);
                return { before, after: location.href,
                    statuses: outcomes.map(value => value.status) };
            });
            assert.equal(result.before, result.after);
            assert.deepEqual(result.statuses, ['rejected', 'rejected']);
            await page.getByText('User navigation').click();
            await page.waitForURL(base + '/blocked-navigation');
            assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });
