import { readFileSync } from 'node:fs';
import { createContext, runInContext } from 'node:vm';

export const runtime = readFileSync(new URL(
    '../../adblock/src/main/resources/adblock/scriptlets.js', import.meta.url), 'utf8');
const extension = readFileSync(new URL(
    '../../adblock/src/main/resources/adblock/scriptlets-extra.js', import.meta.url), 'utf8');
const network = readFileSync(new URL(
    '../../adblock/src/main/resources/adblock/scriptlets-network.js', import.meta.url), 'utf8');
const advanced = readFileSync(new URL(
    '../../adblock/src/main/resources/adblock/scriptlets-advanced.js', import.meta.url), 'utf8');
export function page(rules, { host = 'site.example', protocol = 'https:', before = '',
    globals = {} } = {}) {
    const calls = { open: [], timers: [], intervals: [], frames: new Map(), listeners: [],
        fetch: [], stopped: 0 };
    class Target {
        addEventListener(...args) { calls.listeners.push([this, ...args]); }
        removeEventListener(type, callback) {
            calls.listeners = calls.listeners.filter(row =>
                row[0] !== this || row[1] !== type || row[2] !== callback);
        }
    }
    class Script extends Target {
        constructor(textContent = '', src = '') {
            super(); this.textContent = textContent; this.src = src;
        }
    }
    const document = Object.assign(new Target(), { currentScript: null, readyState: 'loading' });
    let frameId = 0;
    const context = createContext({
        location: { hostname: host, protocol, href: `${protocol}//${host}/path` },
        document, EventTarget: Target, HTMLScriptElement: Script,
        Response: class extends Response {}, Request, DOMException, atob, URL, Headers,
        open(...args) { calls.open.push(args); return 'opened'; },
        setTimeout(...args) { calls.timers.push(args); return calls.timers.length; },
        clearTimeout() {},
        setInterval(...args) { calls.intervals.push(args); return calls.intervals.length; },
        requestAnimationFrame(callback) { calls.frames.set(++frameId, callback); return frameId; },
        cancelAnimationFrame(id) { calls.frames.delete(id); },
        fetch(...args) { calls.fetch.push(args); return Promise.resolve('native response'); },
        stop() { calls.stopped++; },
        ...globals,
    });
    runInContext('self = globalThis; window = self; top = self;', context);
    context.addEventListener = Target.prototype.addEventListener;
    context.removeEventListener = Target.prototype.removeEventListener;
    if (before) { runInContext(before, context); }
    const config = { rules: [], hosts: [], exceptions: [] };
    const hosts = new Map();
    for (const [type, args, domain = 'site.example', excluded = [], exception = false] of rules) {
        const index = config.rules.length;
        config.rules.push([type, args, excluded, exception]);
        if (domain === '') { config.exceptions.push(index); }
        else {
            if (!hosts.has(domain)) { hosts.set(domain, []); }
            hosts.get(domain).push(index);
        }
    }
    config.hosts = Array.from(hosts.entries());
    const domains = '|' + Array.from(hosts.keys()).join('|') + '|';
    const names = [...new Set(rules.filter(rule => rule[0] === 'RESOURCE')
        .map(rule => rule[1][0]))];
    const factories = names.map(name => {
        // Tests load the same fixed local sources as Kotlin, never filter-provided JS.
        if (!/^[a-z0-9_-]+\.js$/.test(name)) { throw new Error('Invalid fixture resource'); }
        const code = readFileSync(new URL(
            '../../adblock/src/main/resources/adblock/ubo/' + name, import.meta.url), 'utf8');
        return JSON.stringify(name) + ':function(setTimeout,clearTimeout){\n' + code + '\n}';
    }).join(',');
    const source = `${runtime}(${JSON.stringify(domains)}, ` +
        `${JSON.stringify(JSON.stringify(config))}, ${extension}, {${factories}}, ` +
        `${network}, ${advanced});`;
    return {
        source, calls, context, document: context.document, Script,
        run: code => runInContext(code, context),
        install: () => runInContext(source, context),
        emit(type, event = {}, owner = context.self) {
            for (const row of [...calls.listeners]) {
                if (row[0] !== owner || row[1] !== type) { continue; }
                row[2](event);
                if (row[3]?.once) { owner.removeEventListener(type, row[2]); }
            }
        },
        flushFrames() {
            const callbacks = [...calls.frames.values()]; calls.frames.clear();
            for (const callback of callbacks) { callback(); }
        },
    };
}
export function installed(type, args, options) {
    const fixture = page([[type, args]], options);
    fixture.install();
    return fixture;
}
