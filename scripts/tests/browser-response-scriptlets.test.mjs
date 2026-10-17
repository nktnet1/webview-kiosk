import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
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
        if (req.url.startsWith('/node-text')) {
            if (req.url.endsWith('-csp')) {
                res.setHeader('Content-Security-Policy', "require-trusted-types-for 'script'");
            }
            return send('text/html', '<!doctype html><script>window.advertValue = 1;' +
                'window.keepValue = 2;</script><body><main>Content</main></body>');
        }
        const media = new Map([
            ['/silent.mp3', ['noop-0.1s.mp3', 'audio/mpeg']],
            ['/silent.mp4', ['noop-1s.mp4', 'video/mp4']],
        ]).get(req.url);
        if (media) {
            return send(media[1], readFileSync(new URL(
                '../../adblock/src/main/resources/adblock/ubo/' + media[0], import.meta.url)));
        }
        if (req.url === '/manifest.xml') {
            return send('text/xml',
                '<MPD><Period><Ad id="ads"/><Content id="video"/></Period></MPD>');
        }
        if (req.url === '/dash.mpd') {
            return send('text/xml', '<MPD mediaPresentationDuration="PT30S">' +
                '<Period id="ad-PT0S" start="PT0S" duration="PT10S">' +
                '<EventStream schemeIdUri="urn:test:ads"/></Period>' +
                '<Period id="content-PT10S" start="PT10S" duration="PT20S"/></MPD>');
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

test('Chromium trusted node replacement runs before inline scripts and preserves Trusted Types CSP',
    options, async () => {
        const { context, page, errors } = await open([['TRUSTED_REPLACE_NODE_TEXT',
            ['script', 'advertValue = 1', 'advertValue = 0', 'sedCount', '1']]]);
        try {
            await page.goto(base + '/node-text');
            assert.equal(await page.evaluate(() => advertValue), 0);
            assert.equal(await page.evaluate(() => keepValue), 2);
            await page.goto(base + '/node-text-csp');
            assert.equal(await page.evaluate(() => advertValue), 1);
            assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium trusted clicks wait for sequential elements and click each step once', options,
    async () => {
        const { context, page, errors } = await open([['TRUSTED_CLICK_ELEMENT',
            ['#first, #second', '', '10']]]);
        try {
            await page.evaluate(() => {
                window.clicked = [];
                const button = document.createElement('button'); button.id = 'first';
                button.onclick = () => {
                    clicked.push('first');
                    const next = document.createElement('button'); next.id = 'second';
                    next.onclick = () => clicked.push('second'); document.body.append(next);
                };
                document.body.append(button);
            });
            await page.waitForFunction(() => clicked.length === 2);
            await page.evaluate(() => document.body.classList.add('changed'));
            assert.deepEqual(await page.evaluate(() => clicked), ['first', 'second']);
            assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium click lookup observes late shadow-root content', options, async () => {
    const { context, page, errors } = await open([['TRUSTED_CLICK_ELEMENT',
        ['#host >>> button', '', '']]]);
    try {
        await page.evaluate(() => {
            window.clicked = 0;
            const host = document.createElement('div'); host.id = 'host';
            host.attachShadow({ mode: 'open' }); document.body.append(host);
        });
        await page.waitForTimeout(25);
        await page.evaluate(() => {
            const button = document.createElement('button'); button.onclick = () => clicked++;
            document.querySelector('#host').shadowRoot.append(button);
        });
        await page.waitForFunction(() => clicked === 1); assert.deepEqual(errors, []);
    } finally { await context.close(); }
});

test('Chromium MPEG-DASH pruning adjusts Period starts and the presentation duration', options,
    async () => {
        const { context, page, errors } = await open([['MPEGDASH_PRUNE',
            ['Period:has(> EventStream[schemeIdUri="urn:test:ads"])', '/dash']]]);
        try {
            const result = await page.evaluate(() => new Promise(resolve => {
                const xhr = new XMLHttpRequest(); xhr.open('GET', '/dash.mpd');
                xhr.responseType = 'document'; xhr.onload = () => {
                    const root = xhr.responseXML.documentElement;
                    resolve({ duration: root.getAttribute('mediaPresentationDuration'),
                        periods: Array.from(root.children).map(node =>
                            [node.getAttribute('start'), node.getAttribute('duration')]) });
                };
                xhr.send();
            }));
            assert.deepEqual(result, { duration: 'PT20S',
                periods: [['PT0S', 'PT0S'], ['PT0S', 'PT20S']] });
            assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium cosmetic text conditions inspect matching children without hiding other cards',
    options, async () => {
        const { context, page, errors } = await open([]);
        try {
            await page.evaluate(() => {
                document.body.innerHTML = '<div class="card" id="advert">' +
                    '<span class="label">Sponsored</span></div>' +
                    '<div class="card" id="content"><span class="label">Content</span>' +
                    '<span>Sponsored</span></div>';
                window.wkCosmeticRules = { postMessage(raw) {
                    const query = JSON.parse(raw);
                    this.onmessage({ data: JSON.stringify({ id: query.id, active: true,
                        selectors: [], procedures: [{ selector: '.card',
                            text: '/^(Sponsored|Sponsorisé)$/', textPattern: true,
                            textSelector: ':scope > .label', upward: 0, remove: false,
                            style: null, removeAttribute: null, removeClass: null,
                            upwardSelector: null }] }) });
                } };
            });
            const source = new URL('../../app/src/main/assets/content-blocking/cosmetic.js',
                import.meta.url);
            await page.addScriptTag({ content: readFileSync(source, 'utf8') });
            await page.waitForFunction(() =>
                document.querySelector('#advert').style.display === 'none');
            const display = await page.evaluate(() =>
                document.querySelector('#content').style.display);
            assert.equal(display, '');
            assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium bundled redirect API shims retain their completion callbacks', options, async () => {
    const { context, page, errors } = await open([]);
    try {
        for (const name of ['amazon_apstag.js', 'chartbeat.js', 'fingerprint2.js']) {
            const source = new URL('../../adblock/src/main/resources/adblock/ubo/' + name,
                import.meta.url);
            await page.addScriptTag({ content: readFileSync(source, 'utf8') });
        }
        const result = await page.evaluate(async () => ({
            bids: await new Promise(resolve => apstag.fetchBids({}, resolve)),
            fingerprint: await new Promise(resolve => Fingerprint2.get(resolve)),
            chartbeat: typeof pSUPERFLY.virtualPage,
        }));
        assert.deepEqual(result.bids, []); assert(Array.isArray(result.fingerprint));
        assert.equal(result.chartbeat, 'function'); assert.deepEqual(errors, []);
    } finally { await context.close(); }
});

test('Chromium click conditions and element method overrides preserve ordinary clicks', options,
    async () => {
        const { context, page, errors } = await open([
            ['TRUSTED_CLICK_ELEMENT', ['#protected', 'cookie:missing=required', '']],
            ['TRUSTED_OVERRIDE_ELEMENT_METHOD', ['HTMLElement.prototype.click', '.advert', '']],
        ]);
        try {
            assert.deepEqual(await page.evaluate(() => {
                const calls = [];
                for (const name of ['advert', 'content', 'protected']) {
                    const button = document.createElement('button'); button.id = name;
                    button.className = name; button.onclick = () => calls.push(name);
                    document.body.append(button);
                    if (name !== 'protected') { button.click(); }
                }
                return calls;
            }), ['content']);
            assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium trusted HTML inserts bounded copies once and removes them after the duration',
    options, async () => {
        const { context, page, errors } = await open([['TRUSTED_CREATE_HTML',
            ['.target', '<span class="injected">Safe text</span>', '100', 'limit', '2']]]);
        try {
            await page.evaluate(() => {
                for (let i = 0; i < 3; i++) {
                    const target = document.createElement('div'); target.className = 'target';
                    document.body.append(target);
                }
            });
            await page.waitForFunction(() => document.querySelectorAll('.injected').length === 2);
            await page.waitForFunction(() => document.querySelectorAll('.injected').length === 0);
            assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium frame bypass protection delegates only selected same-origin APIs', options,
    async () => {
        const { context, page, errors } = await open([
            ['JSON_PRUNE', ['ads', '']],
            ['TRUSTED_PREVENT_DOM_BYPASS', ['Node.prototype.appendChild', 'JSON.parse']],
        ]);
        try {
            const result = await page.evaluate(() => {
                const frame = document.createElement('iframe');
                const appended = document.body.appendChild(frame);
                return { same: appended === frame,
                    parsed: frame.contentWindow.JSON.parse('{"ads":1,"content":2}'),
                    unchanged: frame.contentWindow.Array !== Array };
            });
            assert.deepEqual(result, { same: true, parsed: { content: 2 }, unchanged: true });
            assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium clipboard protection retains safe text and native clipboard permissions', options,
    async () => {
        const { context, page, errors } = await open([['PREVENT_CLIPBOARD_WRITE', ['danger']]]);
        try {
            await context.grantPermissions(['clipboard-read', 'clipboard-write']);
            const result = await page.evaluate(async () => {
                await navigator.clipboard.writeText('content');
                await navigator.clipboard.writeText('danger');
                return navigator.clipboard.readText();
            });
            assert.equal(result, 'content'); assert.deepEqual(errors, []);
        } finally { await context.close(); }
    });

test('Chromium decodes the bundled silent MP3 and MP4 redirect resources', options, async () => {
    const { context, page, errors } = await open([]);
    try {
        const durations = await page.evaluate(async () => Promise.all(
            ['audio', 'video'].map(type => new Promise((resolve, reject) => {
                const media = document.createElement(type);
                media.onloadedmetadata = () => resolve(media.duration);
                media.onerror = () => reject(new Error('Unsupported fixture media'));
                media.src = type === 'audio' ? '/silent.mp3' : '/silent.mp4';
                media.load();
            }))));
        assert(durations.every(value => Number.isFinite(value) && value > 0));
        assert.deepEqual(errors, []);
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
