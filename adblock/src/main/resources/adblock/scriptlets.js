/*******************************************************************************

    uBlock Origin - a comprehensive, efficient content blocker
    Copyright (C) 2019-present Raymond Hill

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see {http://www.gnu.org/licenses/}.

    Home: https://github.com/gorhill/uBlock

*/

/*
 * Adapted portions: uBlock Origin scriptlet resources, Raymond Hill, GPL-3.0-or-later.
 * Modified for Webview Kiosk on 2026-10-16: bounded typed instructions, shared hooks,
 * own-frame routing, conservative argument subsets, and WebView resource limits.
 * See META-INF/ublock-NOTICE.txt and META-INF/licenses/ublock-GPL-3.0.txt.
 * Filter arguments are JSON data, never JavaScript source. See SCRIPTLETS.md.
 * Keep this a function expression; Kotlin appends the host gate and JSON payload.
 */
(function(domains, serialized) {
    'use strict';
    if (location.protocol !== 'https:' && location.protocol !== 'http:') { return; }
    const host = location.hostname.toLowerCase().replace(/\.$/, '');
    if (host === 'appassets.androidplatform.net' ||
        host === 'pdf-dummy.webviewkiosk.nktnet.uk') { return; }
    let candidate = host;
    let eligible = false;
    while (candidate) {
        if (domains.includes('|' + candidate + '|')) { eligible = true; break; }
        const dot = candidate.indexOf('.');
        if (dot === -1) { break; }
        candidate = candidate.slice(dot + 1);
    }
    if (!eligible) { return; }
    const config = JSON.parse(serialized);
    const domainMatches = (name) => host === name || host.endsWith('.' + name);
    const candidates = new Set(config.exceptions);
    const hostIndex = new Map(config.hosts);
    let suffix = host;
    while (suffix) {
        const bucket = hostIndex.get(suffix) || [];
        for (const index of bucket) { candidates.add(index); }
        const dot = suffix.indexOf('.');
        if (dot === -1) { break; }
        suffix = suffix.slice(dot + 1);
    }
    const matching = Array.from(candidates).sort((a, b) => a - b)
        .map(index => config.rules[index])
        .filter(rule => !rule[2].some(domainMatches));
    if (matching.some(rule => rule[3] && rule[0] === null)) { return; }
    const key = rule => JSON.stringify([rule[0], rule[1]]);
    const excepted = new Set(matching.filter(rule => rule[3]).map(key));
    const seen = new Set();
    const selected = matching.filter(rule => {
        const id = key(rule);
        if (rule[3] || excepted.has(id) || seen.has(id) || seen.size >= 128) { return false; }
        seen.add(id);
        return true;
    });
    if (selected.length === 0) { return; }

    // Capture intrinsics before any selected property rule can change them.
    const apply = Reflect.apply;
    const reflectGet = Reflect.get;
    const define = Object.defineProperty;
    const descriptor = Object.getOwnPropertyDescriptor;
    const ownKeys = Object.keys;
    const isArray = Array.isArray;
    const stringifyFunction = Function.prototype.toString;
    const nativeAdd = EventTarget.prototype.addEventListener;
    const nativeThen = Promise.prototype.then;
    const resolved = Promise.resolve.bind(Promise);
    const rejected = Promise.reject.bind(Promise);
    const NativeResponse = self.Response;
    const NativeRequest = self.Request;
    const NativeError = ReferenceError;
    const installedScript = document.currentScript;
    const object = value => value !== null &&
        (typeof value === 'object' || typeof value === 'function');
    const functionTexts = new WeakMap();
    let cachedTexts = 0;
    let cachedBytes = 0;
    function source(value) {
        if (typeof value === 'string') { return value.length <= 65536 ? value : null; }
        if (typeof value !== 'function') { return null; }
        let text = functionTexts.get(value);
        if (text !== undefined) { return text; }
        text = apply(stringifyFunction, value, []);
        if (text.length > 65536) { return null; }
        if (cachedTexts < 512 && cachedBytes + text.length <= 262144) {
            functionTexts.set(value, text);
            cachedTexts++;
            cachedBytes += text.length;
        }
        return text;
    }
    function matcher(raw = '', negate = false) {
        const inverted = negate && raw.startsWith('!');
        if (inverted) { raw = raw.slice(1); }
        const end = raw.lastIndexOf('/');
        const regex = raw.startsWith('/') && end > 0
            ? new RegExp(raw.slice(1, end), raw.slice(end + 1)) : null;
        return value => {
            if (typeof value !== 'string' || value.length > 65536) { return false; }
            const match = regex ? regex.test(value) : value.includes(raw);
            return inverted ? !match : match;
        };
    }
    function wrap(owner, name, intercept) {
        if (!owner || typeof owner[name] !== 'function') { return; }
        const original = owner[name];
        const desc = descriptor(owner, name);
        if (desc && !('value' in desc)) { return; }
        const proxy = new Proxy(original, {
            apply(target, receiver, args) {
                return intercept(target, receiver, args);
            },
        });
        define(owner, name, desc ? { ...desc, value: proxy } : {
            value: proxy, configurable: true, writable: true,
        });
    }

    const groups = new Map();
    for (const rule of selected) {
        if (!groups.has(rule[0])) { groups.set(rule[0], []); }
        groups.get(rule[0]).push(rule[1]);
    }
    const aborting = ['ABORT_READ', 'ABORT_WRITE', 'ABORT_CURRENT_SCRIPT']
        .some(type => groups.has(type));
    const token = 'wk-scriptlet-' + Math.random().toString(36).slice(2);
    if (aborting) {
        apply(nativeAdd, self, ['error', event => {
            if (event.error instanceof NativeError && event.error.message === token) {
                event.preventDefault();
            }
        }]);
    }
    function abort() { throw new NativeError(token); }
    const slots = new WeakMap();
    let slotCount = 0;
    function slot(owner, name) {
        if (!object(owner)) { return null; }
        let entries = slots.get(owner);
        if (!entries) { entries = new Map(); slots.set(owner, entries); }
        if (entries.has(name)) { return entries.get(name); }
        const desc = descriptor(owner, name);
        if (desc && (!desc.configurable || desc.writable === false ||
            desc.get && !desc.set) || slotCount >= 512) { return null; }
        const initial = owner[name];
        const state = { value: initial, mode: '', readers: [], children: [], constant: null };
        define(owner, name, {
            configurable: true,
            enumerable: desc ? desc.enumerable : true,
            get() {
                if (state.mode === 'ABORT_READ') { abort(); }
                for (const check of state.readers) { check(); }
                if (state.mode === 'ABORT_WRITE') { return undefined; }
                if (state.constant && !state.constant.released) { return state.constant.value; }
                return desc && desc.get ? apply(desc.get, this, []) : state.value;
            },
            set(value) {
                if (state.mode === 'ABORT_WRITE') { abort(); }
                if (state.mode === 'ABORT_READ') { return; }
                for (const check of state.readers) { check(); }
                if (state.constant && !state.constant.released) {
                    const fixed = state.constant.value;
                    if (value == null || fixed == null || typeof value === typeof fixed) { return; }
                    // uBO relinquishes constants on a type-changing write.
                    state.constant.released = true;
                }
                if (desc && desc.set) { apply(desc.set, this, [value]); }
                else if (!desc || !desc.get && desc.writable !== false) { state.value = value; }
                const actual = desc && desc.get ? apply(desc.get, this, []) : state.value;
                for (const child of state.children) { child(actual); }
            },
        });
        entries.set(name, state);
        slotCount++;
        return state;
    }
    function trap(owner, parts, install) {
        if (!object(owner)) { return; }
        const name = parts[0];
        const state = slot(owner, name);
        if (parts.length === 1) {
            if (state) {
                install(state);
                if (state.mode) {
                    define(owner, name, { ...descriptor(owner, name), configurable: false });
                }
            }
            return;
        }
        const rest = parts.slice(1);
        if (state) {
            const child = value => { try { trap(value, rest, install); } catch {} };
            state.children.push(child);
            child(state.value);
        } else {
            // A read-only ancestor may still contain configurable leaf properties.
            const desc = descriptor(owner, name);
            if (desc) { trap(owner[name], rest, install); }
        }
    }
    function constant(raw) {
        switch (raw) {
        case 'undefined': return undefined;
        case 'null': return null;
        case 'true': return true;
        case 'false': return false;
        case '': return '';
        case '[]': return [];
        case '{}': return {};
        case 'noopFunc': return function() {};
        case 'trueFunc': return function() { return true; };
        case 'falseFunc': return function() { return false; };
        case 'throwFunc': return function() { throw ''; };
        default: return Number(raw);
        }
    }
    const textGetter = groups.has('ABORT_CURRENT_SCRIPT') && self.Node
        ? descriptor(Node.prototype, 'textContent').get : null;
    function scriptCheck(args) {
        const text = matcher(args[1]);
        const context = matcher(args[2]);
        return () => {
            const script = document.currentScript;
            if (!script || script === installedScript ||
                !(script instanceof HTMLScriptElement)) { return; }
            if (args[2] !== '' && !context(script.src)) { return; }
            let content = textGetter ? apply(textGetter, script, []) : script.textContent;
            if (!content.trim() && script.src.startsWith('data:')) {
                const comma = script.src.indexOf(',');
                if (comma >= 0 && script.src.length <= 65536) {
                    try {
                        content = script.src.slice(0, comma).endsWith(';base64')
                            ? atob(script.src.slice(comma + 1))
                            : decodeURIComponent(script.src.slice(comma + 1));
                    } catch { return; }
                }
            }
            if (text(content)) { abort(); }
        };
    }
    // Iterate the original list order, so conflicting terminal property hooks use
    // the first instruction. Multiple ACS checks share a single accessor pair.
    function installProperty(type, args) {
        if (!['SET', 'ABORT_READ', 'ABORT_WRITE', 'ABORT_CURRENT_SCRIPT'].includes(type)) {
            return;
        }
        try {
            const check = type === 'ABORT_CURRENT_SCRIPT' ? scriptCheck(args) : null;
            trap(self, args[0].split('.'), state => {
                if (state.mode && !(state.mode === type && check)) { return; }
                if (type === 'SET') {
                    let value = constant(args[1]);
                    if (['noopFunc', 'trueFunc', 'falseFunc'].includes(args[1])) {
                        const name = args[0].split('.').slice(-1)[0];
                        define(value, 'name', { value: name });
                        value = new Proxy(value, {
                            get(target, prop, receiver) {
                                if (prop === 'toString') {
                                    return () => 'function ' + name + '() { [native code] }';
                                }
                                return reflectGet(target, prop, receiver);
                            },
                        });
                    }
                    if (state.value != null && value != null &&
                        typeof state.value !== typeof value) { return; }
                    state.constant = { value, released: false };
                }
                state.mode = type;
                if (check) { state.readers.push(check); }
            });
        } catch { /* A non-configurable property must never break page loading. */ }
    }

    function installWindowOpen() {
        const tests = groups.get('PREVENT_WINDOW_OPEN').map(args => matcher(args[0], true));
        try {
            wrap(self, 'open', (target, receiver, args) => {
                // Let the native function handle unusual coercions and invalid calls.
                if (args.every(arg => typeof arg === 'string' || arg == null)) {
                    const text = args.join(' ');
                    if (tests.some(test => test(text))) { return null; }
                }
                return apply(target, receiver, args);
            });
        } catch {}
    }
    function installListener() {
        const tests = groups.get('PREVENT_ADD_EVENT_LISTENER').map(args => ({
            type: matcher(args[0]), handler: matcher(args[1]), anyHandler: args[1] === '',
        }));
        try {
            wrap(EventTarget.prototype, 'addEventListener', (target, receiver, args) => {
                if (typeof args[0] !== 'string') { return apply(target, receiver, args); }
                let callback = args[1];
                try {
                    if (callback && typeof callback === 'object') {
                        callback = callback.handleEvent;
                    }
                    let text;
                    for (const test of tests) {
                        if (!test.type(args[0]) || callback == null) { continue; }
                        if (test.anyHandler) { return; }
                        if (text === undefined) { text = source(callback); }
                        if (test.handler(text)) { return; }
                    }
                } catch {}
                return apply(target, receiver, args);
            });
        } catch {}
    }
    function installTimeout() {
        const tests = groups.get('PREVENT_SET_TIMEOUT').map(args => {
            const raw = args[1].replace(/^!/, '');
            const bounds = raw.split('-');
            const low = bounds[0] === '' ? 0 : Number(bounds[0]);
            const high = bounds.length === 1 ? low
                : bounds[1] === '' ? Infinity : Number(bounds[1]);
            return { callback: matcher(args[0], true), anyCallback: args[0] === '',
                delay: value => {
                    if (args[1] === '') { return true; }
                    const match = value >= low && value <= high;
                    return args[1].startsWith('!') ? !match : match;
                } };
        });
        try {
            wrap(self, 'setTimeout', (target, receiver, args) => {
                if ((typeof args[0] === 'function' || typeof args[0] === 'string') &&
                    (args[1] === undefined || ['number', 'string'].includes(typeof args[1]))) {
                    const delay = args[1] === undefined ? 0 : Number(args[1]);
                    let text;
                    for (const test of tests) {
                        if (!test.delay(delay)) { continue; }
                        if (!test.anyCallback) {
                            try {
                                if (text === undefined) { text = source(args[0]); }
                                if (!test.callback(text)) { continue; }
                            } catch { continue; }
                        }
                        args[0] = function() {};
                        break;
                    }
                }
                return apply(target, receiver, args);
            });
        } catch {}
    }
    function installJson() {
        const tests = groups.get('JSON_PRUNE').map(args => ({
            prune: args[0].split(' ').map(path => path.split('.')),
            needles: args[1] ? args[1].split(' ').map(path => path.split('.')) : [],
        }));
        function prune(value) {
            if (!object(value)) { return value; }
            for (const test of tests) {
                let budget = 4096;
                const edits = [];
                const walk = (owner, parts, remove) => {
                    if (--budget < 0) { throw null; }
                    if (!object(owner)) { return false; }
                    const part = parts[0];
                    const keys = part === '*' || part === '[]' && isArray(owner)
                        ? ownKeys(owner) : part === '[]' ? [] : [part];
                    let found = false;
                    for (const name of keys) {
                        if (--budget < 0) { throw null; }
                        const desc = descriptor(owner, name);
                        if (!desc || !('value' in desc)) { continue; }
                        if (parts.length === 1) {
                            found = true;
                            if (remove && desc.configurable) { edits.push([owner, name]); }
                        } else if (walk(desc.value, parts.slice(1), remove)) { found = true; }
                    }
                    return found;
                };
                try {
                    if (!test.needles.every(path => walk(value, path, false))) { continue; }
                    for (const path of test.prune) { walk(value, path, true); }
                    for (const [owner, name] of edits) { delete owner[name]; }
                } catch { /* Budget exhaustion makes this instruction a no-op. */ }
            }
            return value;
        }
        try {
            wrap(JSON, 'parse', (target, receiver, args) => prune(apply(target, receiver, args)));
            if (NativeResponse) {
                wrap(NativeResponse.prototype, 'json', (target, receiver, args) =>
                    apply(nativeThen, apply(target, receiver, args), [prune]));
            }
        } catch {}
    }
    function installFetch() {
        if (!NativeResponse) { return; }
        const tests = groups.get('PREVENT_FETCH').map(args => ({
            fields: args[0].split(' ').map(part => {
                const colon = part.indexOf(':');
                const name = part.slice(0, colon);
                return colon > 0 && (name === 'url' || name === 'method')
                    ? [name, matcher(part.slice(colon + 1), true)]
                    : ['url', matcher(part, true)];
            }),
            body: args[1] === 'emptyObj' ? '{}' : args[1] === 'emptyArr' ? '[]' : '',
        }));
        try {
            wrap(self, 'fetch', (target, receiver, args) => {
                let details;
                let signal;
                try {
                    const input = args[0];
                    const request = NativeRequest && input instanceof NativeRequest;
                    if (!request && typeof input !== 'string') {
                        return apply(target, receiver, args);
                    }
                    const init = args[1];
                    details = { url: request ? input.url : input,
                        method: init && init.method !== undefined ? init.method
                            : request ? input.method : 'GET' };
                    signal = init && init.signal !== undefined ? init.signal
                        : request ? input.signal : null;
                    if (typeof details.method !== 'string') {
                        return apply(target, receiver, args);
                    }
                } catch { return apply(target, receiver, args); }
                const test = tests.find(test =>
                    test.fields.every(([name, match]) => match(details[name])));
                if (!test) { return apply(target, receiver, args); }
                if (signal && signal.aborted) {
                    return rejected(signal.reason || new DOMException('Aborted', 'AbortError'));
                }
                const response = new NativeResponse(test.body, {
                    status: 200, statusText: 'OK',
                    headers: { 'Content-Length': String(test.body.length) },
                });
                define(response, 'url', { value: details.url });
                return resolved(response);
            });
        } catch {}
    }
    const installers = new Map([
        ['PREVENT_WINDOW_OPEN', installWindowOpen],
        ['PREVENT_ADD_EVENT_LISTENER', installListener],
        ['PREVENT_SET_TIMEOUT', installTimeout],
        ['JSON_PRUNE', installJson],
        ['PREVENT_FETCH', installFetch],
    ]);
    const installed = new Set();
    for (const [type, args] of selected) {
        const install = installers.get(type);
        if (!install) { installProperty(type, args); }
        else if (!installed.has(type)) {
            installed.add(type);
            install();
        }
    }

})
