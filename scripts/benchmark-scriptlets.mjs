// Host V8 measurements only: not Android renderer, network, or page-load timings.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { Script, createContext } from 'node:vm';
import { performance } from 'node:perf_hooks';

const path = process.argv[2];
if (!path) { throw new Error('Pass the exported ublock-injection.js path'); }
const source = readFileSync(path, 'utf8');
const runtime = readFileSync(process.argv[3] || new URL(
    '../adblock/src/main/resources/adblock/scriptlets.js', import.meta.url), 'utf8');
const median = values => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)];
function timed(run) {
    const start = performance.now();
    run();
    return performance.now() - start;
}
function context(host) {
    class Target { addEventListener() {} }
    const sandbox = createContext({
        location: { protocol: 'https:', hostname: host, href: 'https://' + host + '/' },
        document: { currentScript: null, readyState: 'loading', querySelectorAll: () => [] },
        EventTarget: Target,
        Response: class extends Response {}, Request,
        timerDelays: [], open() { return null; },
        setTimeout(callback, delay) { sandbox.timerDelays.push(delay); return 1; },
        clearTimeout() {},
        fetch() { return Promise.resolve(new Response('')); },
    });
    new Script('self = globalThis; window = self;').runInContext(sandbox);
    sandbox.addEventListener = Target.prototype.addEventListener;
    return sandbox;
}
const compiled = new Script(source);
const output = { node: process.version, sourceBytes: Buffer.byteLength(source) };
output.scriptConstructionMedianMs = median(Array.from({ length: 31 }, (_, index) =>
    timed(() => new Script('/* ' + index + ' */\n' + source))));
for (const host of ['unrelated.example', 'www.youtube.com', 'timesofindia.indiatimes.com']) {
    const times = [];
    for (let i = 0; i < 36; i++) {
        const sandbox = context(host);
        const duration = timed(() => compiled.runInContext(sandbox));
        if (i >= 5) { times.push(duration); }
    }
    output[host] = { documentStartMedianMs: median(times) };
}
// Exercise the actual Kotlin-produced routing, serialization, and bundled code.
for (const host of ['www.youtube.com', 'unrelated.example']) {
    const sandbox = context(host);
    compiled.runInContext(sandbox);
    const result = new Script(`
        ytInitialPlayerResponse = { playerAds: ['advertisement'], title: 'Video' };
        [ytInitialPlayerResponse.playerAds, ytInitialPlayerResponse.title];
    `).runInContext(sandbox);
    assert.equal(result[1], 'Video');
    if (host === 'www.youtube.com') { assert.equal(result[0], undefined); }
    else { assert.equal(result[0][0], 'advertisement'); }
}
const publisher = context('timesofindia.indiatimes.com');
compiled.runInContext(publisher);
assert.equal(new Script('nsShowMaxCount').runInContext(publisher), 0);
for (const host of ['imgdrive.net', 'unrelated.example']) {
    const sandbox = context(host);
    compiled.runInContext(sandbox);
    new Script('setTimeout(function redirect(){}, 4000)').runInContext(sandbox);
    const boosted = host === 'imgdrive.net' && source.includes('ADJUST_SET_TIMEOUT');
    assert.equal(sandbox.timerDelays.at(-1), boosted ? 200 : 4000);
}
output.kotlinPayloadBehaviorChecks = 5;
const count = 200000;
const loop = new Script(`
    function loginCallback() {}
    for (let i = 0; i < ${count}; i++) { setTimeout(loginCallback, 1000); }
`);
function timerCost(rules) {
    const times = [];
    for (let i = 0; i < 16; i++) {
        const sandbox = context('site.example');
        new Script('setTimeout = function(){ return 1; };').runInContext(sandbox);
        if (rules.length) {
            const data = { rules: rules.map(rule => [...rule, []]), entities: [], hosts: [['site.example', rules.map((_, index) => index)]],
                exceptions: [] };
            new Script(`${runtime}(['|site.example|','||'], ${JSON.stringify(JSON.stringify(data))});`)
                .runInContext(sandbox);
        }
        const duration = timed(() => loop.runInContext(sandbox));
        if (i >= 5) { times.push(duration * 1000000 / count); }
    }
    return median(times);
}
output.timerNativeNsPerCall = timerCost([]);
output.timerOneRuleNsPerCall = timerCost([
    ['PREVENT_SET_TIMEOUT', ['advert', ''], [], false],
]);
output.timerEightRulesNsPerCall = timerCost(Array.from({ length: 8 }, (_, index) =>
    ['PREVENT_SET_TIMEOUT', ['advert' + index, ''], [], false]));
console.log(JSON.stringify(output, null, 2));
