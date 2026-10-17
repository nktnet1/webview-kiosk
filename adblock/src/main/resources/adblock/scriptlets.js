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

/* Modified for Webview Kiosk on 2026-10-17: bounded shared hooks and frame routing.
 * Source attribution: META-INF/ublock-NOTICE.txt. Keep this a function expression.
 */
(function(routes, records, instructions, extension, resources, network, advanced) {
    'use strict';
    if (location.protocol !== 'https:' && location.protocol !== 'http:') { return; }
    const host = location.hostname.toLowerCase().replace(/\.$/, '').replace(/^\[|\]$/g, '');
    if (host === 'appassets.androidplatform.net' ||
        host === 'pdf-dummy.webviewkiosk.nktnet.uk') { return; }
    // Binary search immutable newline-delimited keys without building a per-frame map.
    function lookup(rows, value) {
        let low = 0, high = rows.length;
        while (low < high) {
            const mid = Math.floor((low + high) / 2);
            const start = mid === 0 ? 0 : rows.lastIndexOf('\n', mid - 1) + 1;
            const tab = rows.indexOf('\t', start);
            const end = rows.indexOf('\n', start);
            const key = rows.slice(start, tab);
            if (key === value) { return JSON.parse(rows.slice(tab + 1, end)); }
            if (key < value) { low = end + 1; } else { high = start; }
        }
        return [];
    }
    function contains(list, value) {
        let low = 0, high = list.length;
        while (low < high) {
            const mid = Math.floor((low + high) / 2);
            const start = mid === 0 ? 0 : list.lastIndexOf('\n', mid - 1) + 1;
            const end = list.indexOf('\n', start);
            const rule = list.slice(start, end);
            if (rule === value) { return true; }
            if (rule < value) { low = end + 1; } else { high = start; }
        }
        return false;
    }
    let suffixes;
    function entityName(host) {
        if (!routes.suffixes || host.includes(':') || /^\d+\.\d+\.\d+\.\d+$/.test(host) ||
            !host.split('.').slice(0, -1).some(label => contains(routes.entityLabels, label))) {
            return '';
        }
        suffixes ||= JSON.parse(routes.suffixes);
        const labels = host.split('.');
        let suffixLength = 1;
        for (let i = 0; i < labels.length; i++) {
            const suffix = labels.slice(i).join('.');
            if (contains(suffixes[1], suffix)) {
                suffixLength = labels.length - i - 1;
                break;
            }
            if (contains(suffixes[0], suffix) || i < labels.length - 1 &&
                contains(suffixes[0], '*.' + labels.slice(i + 1).join('.'))) {
                suffixLength = Math.max(suffixLength, labels.length - i);
            }
        }
        return labels.slice(0, labels.length - suffixLength).join('.');
    }
    const candidates = new Set(routes.exceptions);
    const collect = (table, name) => {
        while (name) {
            for (const offset of lookup(table, name)) { candidates.add(offset); }
            const dot = name.indexOf('.');
            if (dot === -1) { break; }
            name = name.slice(dot + 1);
        }
    };
    collect(routes.hosts, host);
    const entity = entityName(host);
    collect(routes.entities, entity);
    const ancestors = [];
    let ancestorsKnown = !routes.usesAncestors;
    if (routes.usesAncestors) {
        try {
            const origins = location.ancestorOrigins;
            ancestorsKnown = !!origins && origins.length <= 64;
            if (ancestorsKnown) {
                for (let i = 0; i < origins.length; i++) {
                    const name = new URL(origins.item(i)).hostname.toLowerCase().replace(/\.$/, '');
                    const entity = entityName(name);
                    ancestors.push([name, entity]);
                    collect(routes.ancestors, name);
                    collect(routes.ancestorEntities, entity);
                }
            }
        } catch { ancestorsKnown = false; }
    }
    const href = location.href;
    const pathStart = typeof href === 'string'
        ? href.indexOf('/', location.protocol.length + 2) : -1;
    const path = pathStart < 0 ? '/' : href.slice(pathStart);
    const regexes = new Map();
    const regexMatch = (source, value) => {
        if (value.length > 65536) { return false; }
        let regex = regexes.get(source);
        if (!regex) { regexes.set(source, regex = new RegExp(source)); }
        return regex.test(value);
    };
    if (routes.regex !== '[]') {
        for (const [hostname, pathname, offset] of JSON.parse(routes.regex)) {
            if (regexMatch(hostname, host) && (pathname === null || regexMatch(pathname, path))) {
                candidates.add(offset);
            }
        }
    }
    if (!candidates.size) { return; }
    const domainMatches = (name, domain = host) => domain === name || domain.endsWith('.' + name);
    const entityMatches = name => entity !== '' && domainMatches(name, entity);
    const targetMatches = target => {
        const [hostname, pathname, regex, usesEntity, ancestor] = target;
        const match = (domain, entity) => {
            const name = usesEntity ? entity : domain;
            return name !== '' &&
                (regex ? regexMatch(hostname, name) : domainMatches(hostname, name)) &&
                (pathname === null ||
                    (regex ? regexMatch(pathname, path) : path.startsWith(pathname)));
        };
        return ancestor ? ancestors.some(([name, entity]) => match(name, entity))
            : match(host, entity);
    };
    const matching = Array.from(candidates).sort((a, b) => a - b)
        .map(offset => JSON.parse(records.slice(offset, records.indexOf('\n', offset))))
        .filter(rule => {
            if (rule[2].some(name => domainMatches(name)) || rule[4].some(entityMatches)) {
                return false;
            }
            const targets = rule[5];
            if (!targets.length) { return true; }
            if (!ancestorsKnown && targets.some(target => target[4])) { return false; }
            if (targets.some(target => target[5] && targetMatches(target))) { return false; }
            return rule[6].some(name => domainMatches(name)) || rule[7].some(entityMatches) ||
                targets.every(target => target[5]) && !rule[6].length && !rule[7].length ||
                targets.some(target => !target[5] && targetMatches(target));
        });
    if (matching.some(rule => rule[3] && rule[0] === -1)) { return; }
    const excepted = new Set(matching.filter(rule => rule[3]).map(rule => rule[0]));
    const seen = new Set();
    const selected = matching.filter(rule => {
        const id = rule[0];
        if (rule[3] || excepted.has(id) || seen.has(id) || seen.size >= routes.limit) {
            return false;
        }
        seen.add(id);
        return true;
    }).map(rule => {
        const offset = rule[0];
        return JSON.parse(instructions.slice(offset, instructions.indexOf('\n', offset)));
    });
    if (selected.length === 0) { return; }
    const modules = new Set(selected.map(rule => rule[2]));

    // Capture intrinsics before any selected property rule can change them.
    const apply = Reflect.apply;
    const reflectGet = Reflect.get;
    const define = Object.defineProperty;
    const descriptor = Object.getOwnPropertyDescriptor;
    const ownKeys = Object.keys;
    const isArray = Array.isArray;
    const arraySplice = Array.prototype.splice;
    const stringifyFunction = Function.prototype.toString;
    const stringIncludes = String.prototype.includes;
    const regexpTest = RegExp.prototype.test;
    const nativeAdd = EventTarget.prototype.addEventListener;
    const nativeThen = Promise.prototype.then;
    const nativeParse = JSON.parse;
    const nativeStringify = JSON.stringify;
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
    function matcher(raw = '', negate = false, exact = false) {
        const inverted = negate && raw.startsWith('!');
        if (inverted) { raw = raw.slice(1); }
        const end = raw.lastIndexOf('/');
        const regex = raw.startsWith('/') && end > 0 && /^[dgimsuvy]*$/.test(raw.slice(end + 1))
            ? new RegExp(raw.slice(1, end), raw.slice(end + 1)) : null;
        return value => {
            if (typeof value !== 'string' || value.length > 65536) { return false; }
            const match = regex ? apply(regexpTest, regex, [value])
                : exact && raw !== '' ? value === raw : apply(stringIncludes, value, [raw]);
            return inverted ? !match : match;
        };
    }
    // One proxy per native function even when several families share a hook.
    const wrappers = new WeakMap();
    let installing = true;
    function wrap(owner, name, intercept) {
        if (!owner || typeof owner[name] !== 'function') { return; }
        let names = wrappers.get(owner);
        if (!names) { names = new Map(); wrappers.set(owner, names); }
        const prior = names.get(name);
        if (prior && owner[name] === prior.proxy) {
            prior.handlers.push({ intercept, active: false }); return;
        }
        const original = owner[name];
        const desc = descriptor(owner, name);
        if (desc && !('value' in desc)) { return; }
        const handlers = [{ intercept, active: false }];
        function dispatch(index, receiver, args) {
            if (index < 0 || installing) { return apply(original, receiver, args); }
            const handler = handlers[index];
            // A hook can use the method it intercepts (for example String.includes).
            if (handler.active) { return dispatch(index - 1, receiver, args); }
            handler.active = true;
            const next = function(...values) {
                handler.active = false;
                try { return dispatch(index - 1, this, values); }
                finally { handler.active = true; }
            };
            try { return handler.intercept(next, receiver, args); }
            finally { handler.active = false; }
        }
        const proxy = new Proxy(original, {
            apply(target, receiver, args) { return dispatch(handlers.length - 1, receiver, args); },
        });
        define(owner, name, desc ? { ...desc, value: proxy } : {
            value: proxy, configurable: true, writable: true,
        });
        names.set(name, { proxy, handlers });
    }

    const groups = new Map();
    for (const rule of selected) {
        if (!groups.has(rule[0])) { groups.set(rule[0], []); }
        groups.get(rule[0]).push(rule[1]);
    }
    const aborting = ['ABORT_READ', 'ABORT_WRITE', 'ABORT_CURRENT_SCRIPT', 'ABORT_STACK_TRACE']
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
    function slot(owner, name, acceptsConstant) {
        if (!object(owner)) { return null; }
        let entries = slots.get(owner);
        if (!entries) { entries = new Map(); slots.set(owner, entries); }
        if (entries.has(name)) { return entries.get(name); }
        const desc = descriptor(owner, name);
        if (desc && (!desc.configurable || desc.writable === false ||
            desc.get && !desc.set && !acceptsConstant) || slotCount >= 4096) { return null; }
        let initial;
        try { initial = owner[name]; } catch (error) {
            // WebIDL getters may reject their prototype as a receiver. A constant
            // can still replace the configurable leaf without invoking it again.
            if (!acceptsConstant || !desc?.get) { throw error; }
        }
        if (acceptsConstant && !acceptsConstant(initial)) { return null; }
        const state = { value: initial, mode: '', readers: [], children: [], constant: null,
            readTransforms: [], writeTransforms: [] };
        define(owner, name, {
            configurable: true,
            enumerable: desc ? desc.enumerable : true,
            get() {
                if (state.mode === 'ABORT_READ') { abort(); }
                for (const check of state.readers) { check(); }
                if (state.mode === 'ABORT_WRITE') { return undefined; }
                if (state.constant && !state.constant.released) { return state.constant.value; }
                let value = desc && desc.get ? apply(desc.get, this, []) : state.value;
                for (const transform of state.readTransforms) { value = transform(value); }
                return value;
            },
            set(value) {
                if (state.mode === 'ABORT_WRITE') { abort(); }
                if (state.mode === 'ABORT_READ') { return; }
                for (const check of state.readers) { check(); }
                if (state.constant && !state.constant.released) {
                    const fixed = state.constant.value;
                    if (state.constant.trusted || value == null || fixed == null ||
                        typeof value === typeof fixed) { return; }
                    // uBO relinquishes constants on a type-changing write.
                    state.constant.released = true;
                }
                for (const transform of state.writeTransforms) { value = transform(value); }
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
    function trap(owner, parts, install, acceptsConstant) {
        if (!object(owner)) { return; }
        const name = parts[0];
        const state = slot(owner, name, parts.length === 1 ? acceptsConstant : null);
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
            const child = value => { try { trap(value, rest, install, acceptsConstant); } catch {} };
            state.children.push(child);
            child(state.value);
        } else {
            // A read-only ancestor may still contain configurable leaf properties.
            const desc = descriptor(owner, name);
            if (desc) { trap(owner[name], rest, install, acceptsConstant); }
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
        if (!['SET', 'TRUSTED_SET', 'ABORT_READ', 'ABORT_WRITE',
            'ABORT_CURRENT_SCRIPT'].includes(type)) {
            return;
        }
        try {
            const check = type === 'ABORT_CURRENT_SCRIPT' ? scriptCheck(args) : null;
            const isConstant = type === 'SET' || type === 'TRUSTED_SET';
            const trusted = type === 'TRUSTED_SET';
            let value;
            let acceptsConstant;
            if (isConstant) {
                value = trusted && args[1].startsWith('json:')
                    ? apply(nativeParse, JSON, [args[1].slice(5)])
                    : trusted && args[1].startsWith('{') && args[1] !== '{}'
                        ? apply(nativeParse, JSON, [args[1]]).value : constant(args[1]);
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
                acceptsConstant = prior => trusted || prior == null || value == null ||
                    typeof prior === typeof value;
            }
            trap(self, args[0].split('.'), state => {
                if (state.mode && !(state.mode === type && check)) { return; }
                if (isConstant) {
                    if (!acceptsConstant(state.value)) { return; }
                    state.constant = { value, released: false, trusted };
                }
                state.mode = type;
                if (check) { state.readers.push(check); }
            }, acceptsConstant);
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
        const stage = value => value === 'complete' ? 2 : value === 'interactive' ? 1 : 0;
        const matches = self.Element?.prototype.matches;
        const tests = groups.get('PREVENT_ADD_EVENT_LISTENER').map(args => {
            const extra = new Map();
            for (let i = 2; i < args.length; i += 2) { extra.set(args[i], args[i + 1]); }
            return { type: matcher(args[0], false, true), handler: matcher(args[1]),
                anyHandler: args[1] === '', at: stage(extra.get('runAt')),
                elements: extra.get('elements') || '', protect: extra.get('protect') === '1',
                active: false };
        });
        const owners = [EventTarget.prototype];
        for (const owner of [document, self]) {
            if (descriptor(owner, 'addEventListener')) { owners.push(owner); }
        }
        const installed = new Set(), protectedOwners = new Set();
        const intercept = (target, receiver, args) => {
                if (typeof args[0] !== 'string') { return apply(target, receiver, args); }
                let callback = args[1];
                try {
                    if (callback && typeof callback === 'object') {
                        callback = callback.handleEvent;
                    }
                    let text;
                    for (const test of tests) {
                        if (!test.active || !test.type(args[0]) || callback == null) { continue; }
                        if (test.elements) {
                            const element = test.elements === 'window' ? receiver === self
                                : test.elements === 'document' ? receiver === document
                                    : matches && self.Element && receiver instanceof self.Element &&
                                        apply(matches, receiver, [test.elements]);
                            if (!element) { continue; }
                        }
                        if (test.anyHandler) { return; }
                        if (text === undefined) { text = source(callback); }
                        if (test.handler(text)) { return; }
                    }
                } catch {}
                return apply(target, receiver, args);
        };
        const activate = () => {
            const ready = stage(document.readyState);
            for (const test of tests) { test.active ||= ready >= test.at; }
            if (!tests.some(test => test.active)) { return; }
            for (const owner of owners) {
                try {
                    if (!installed.has(owner)) {
                        wrap(owner, 'addEventListener', intercept); installed.add(owner);
                    }
                    if (!protectedOwners.has(owner) &&
                        tests.some(test => test.active && test.protect)) {
                        const fn = owner.addEventListener;
                        define(owner, 'addEventListener', { get: () => fn, set() {} });
                        protectedOwners.add(owner);
                    }
                } catch {}
            }
        };
        activate();
        if (tests.some(test => !test.active)) {
            apply(nativeAdd, document, ['readystatechange', activate]);
        }
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
    function jsonPruner(argumentsList) {
        const tests = argumentsList.map(args => ({
            prune: args[0].split(' ').map(path => path.split('.')),
            needles: args[1] ? args[1].split(' ').map(path => path.split('.')) : [],
        }));
        function prune(value, check = false) {
            if (!object(value)) { return check ? false : value; }
            for (const test of tests) {
                let budget = 4096;
                const edits = [];
                const walk = (owner, parts, remove) => {
                    if (--budget < 0) { throw null; }
                    if (!object(owner)) { return false; }
                    const part = parts[0];
                    const deleting = part === '[-]' || part === '{-}';
                    const array = part === '[]' || part === '[-]';
                    let keys = part === '*' || part === '{}' || part === '{-}' ||
                        array && isArray(owner) ? ownKeys(owner) : array ? [] : [part];
                    if (part === '[-]') {
                        keys = keys.filter(key => /^(?:0|[1-9]\d*)$/.test(key) &&
                            Number(key) < owner.length);
                    }
                    let found = false;
                    for (const name of keys) {
                        if (--budget < 0) { throw null; }
                        const desc = descriptor(owner, name);
                        if (!desc || !('value' in desc)) { continue; }
                        if (parts.length === 1) {
                            found = true;
                            if (remove && desc.configurable) { edits.push([owner, name, false]); }
                        } else if (walk(desc.value, parts.slice(1), remove && !deleting)) {
                            found = true;
                            if (remove && deleting && desc.configurable) {
                                edits.push([owner, name, part === '[-]']);
                            }
                        }
                    }
                    return found;
                };
                try {
                    if (!test.needles.every(path => walk(value, path, false))) { continue; }
                    if (check) { return true; }
                    for (const path of test.prune) { walk(value, path, true); }
                    const removed = new WeakMap();
                    const pending = edits.filter(([owner, name]) => {
                        let names = removed.get(owner);
                        if (!names) { names = new Set(); removed.set(owner, names); }
                        if (names.has(name)) { return false; }
                        names.add(name); return true;
                    });
                    const arrays = new Map();
                    for (const [owner, name, splice] of pending) {
                        if (!splice) { delete owner[name]; continue; }
                        if (!arrays.has(owner)) { arrays.set(owner, []); }
                        arrays.get(owner).push(Number(name));
                    }
                    for (const [owner, indices] of arrays) {
                        for (const index of indices.sort((a, b) => b - a)) {
                            apply(arraySplice, owner, [index, 1]);
                        }
                    }
                } catch { /* Budget exhaustion makes this instruction a no-op. */ }
            }
            return check ? false : value;
        }
        return prune;
    }
    // The compiler supplies a JSON AST, never expressions to evaluate as JavaScript.
    function jsonEditor(args) {
        const [v2, steps, guards = [], mutation = null] = nativeParse(args[1]);
        const stringKeys = ownKeys;
        const regexes = new Map();
        const prepare = node => {
            if (isArray(node)) { node.forEach(prepare); }
            else if (node && typeof node === 'object' && typeof node.regex === 'string') {
                regexes.set(node, new RegExp(node.regex, node.flags));
            }
        };
        prepare(steps); prepare(guards);
        const testRegex = (node, value) => {
            const text = String(value);
            return text.length <= 65536 && apply(regexpTest, regexes.get(node), [text]);
        };
        function edit(value, work = { remaining: 16384 }) {
            const root = { value };
            const consume = () => { if (--work.remaining < 0) { throw null; } };
            const ref = (owner, key) => {
                consume();
                const desc = descriptor(owner, key);
                return { owner, key, exists: !!desc && 'value' in desc,
                    value: desc && 'value' in desc ? desc.value : undefined };
            };
            const keys = owner => {
                consume();
                const values = stringKeys(owner);
                if (values.length > work.remaining) { throw null; }
                work.remaining -= values.length;
                return isArray(owner) ? values.map(key => /^(?:0|[1-9]\d*)$/.test(key)
                    ? Number(key) : key) : values;
            };
            const children = owner => typeof owner === 'object' && owner !== null
                ? keys(owner).map(key => ref(owner, key)) : [];
            const descendants = owner => {
                const out = [], stack = [[owner, 0]], visited = new Set();
                while (stack.length) {
                    consume();
                    const [value, depth] = stack.pop();
                    if (typeof value !== 'object' || value === null) { continue; }
                    if (depth > 64 || visited.has(value)) { throw null; }
                    visited.add(value); out.push(value);
                    for (const child of children(value).reverse()) {
                        if (child.exists) { stack.push([child.value, depth + 1]); }
                    }
                }
                return out;
            };
            const compare = (entry, predicate) => {
                const [negated, , op, rhs] = predicate;
                if (!op) { return entry.exists !== negated; }
                if (!entry.exists) { return false; }
                const value = entry.value;
                let matched;
                switch (op) {
                    case '==': matched = value === rhs; break;
                    case '!=': matched = value !== rhs; break;
                    case '<': matched = value < rhs; break;
                    case '<=': matched = value <= rhs; break;
                    case '>': matched = value > rhs; break;
                    case '>=': matched = value >= rhs; break;
                    case '^=': matched = String(value).startsWith(rhs); break;
                    case '$=': matched = String(value).endsWith(rhs); break;
                    case '*=': matched = String(value).includes(rhs); break;
                    case 're': matched = testRegex(rhs, value); break;
                    default: return false;
                }
                return matched !== negated;
            };
            const evaluate = (program, input, predicate) => {
                if (!program.length && predicate) {
                    return input.filter(entry => compare(entry, predicate));
                }
                let entries = input;
                program.forEach(([recursive, selectors, filter], stepIndex) => {
                    const result = [];
                    for (const entry of entries) {
                        consume();
                        const value = entry.value;
                        if (typeof value !== 'object' || value === null) { continue; }
                        if (filter !== null) {
                            let candidates;
                            if (!recursive && !v2 && !isArray(value)) {
                                candidates = keys(value).length ? [entry] : [];
                            } else {
                                const owners = recursive ? descendants(value) : [value];
                                candidates = owners.flatMap(children);
                            }
                            for (const candidate of candidates) {
                                const input = filter[4] ? [ref(root, 'value')] : [candidate];
                                if (evaluate(filter[1], input, filter).length) {
                                    result.push(candidate);
                                }
                            }
                        } else {
                            for (const owner of recursive ? descendants(value) : [value]) {
                                for (const selector of selectors) {
                                    let names;
                                    if (selector === '*') { names = keys(owner); }
                                    else if (typeof selector === 'number') {
                                        names = !isArray(owner) ? []
                                            : [selector < 0 ? owner.length + selector : selector];
                                    } else if (typeof selector === 'object') {
                                        names = keys(owner).map(String)
                                            .filter(name => testRegex(selector, name));
                                    } else { names = [selector]; }
                                    for (const name of names) {
                                        const candidate = ref(owner, name);
                                        if (stepIndex === program.length - 1 && predicate
                                            ? compare(candidate, predicate) : candidate.exists) {
                                            result.push(candidate);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (result.length > 4096) { throw null; }
                    entries = result;
                });
                return entries;
            };
            try {
                const input = [ref(root, 'value')];
                for (const [path, minimum, maximum] of guards) {
                    const count = evaluate(path, input).length;
                    if (count < minimum || count > maximum) { return null; }
                }
                const edits = evaluate(steps, input);
                const seen = new WeakMap(), pending = [];
                for (const entry of edits) {
                    const desc = descriptor(entry.owner, entry.key);
                    if (['__proto__', 'prototype', 'constructor'].includes(String(entry.key))) {
                        continue;
                    }
                    if (mutation ? mutation[0] !== 'call' &&
                        desc && (!('value' in desc) || !desc.writable)
                        : !desc || !desc.configurable) { continue; }
                    let names = seen.get(entry.owner);
                    if (!names) { seen.set(entry.owner, names = new Set()); }
                    if (names.has(entry.key)) { continue; }
                    names.add(entry.key); pending.push(entry);
                }
                // Traversal completes before any mutation, including on budget exhaustion.
                for (const { owner, key } of pending.reverse()) {
                    if (mutation) {
                        const [operator, raw] = mutation;
                        if (operator === 'call') {
                            const values = raw.map(value => value === '${obj}' ? owner
                                : value === '${key}' ? key : value === '${val}'
                                    ? descriptor(owner, key)?.value : value);
                            const receiver = values[0] ?? self;
                            const method = receiver[values[1]];
                            if (typeof method === 'function') {
                                apply(method, receiver, values.slice(2));
                            }
                            continue;
                        }
                        let value = typeof raw === 'string'
                            ? raw.replace('${now}', String(Date.now())) : raw;
                        if (operator === '+') {
                            const target = descriptor(owner, key)?.value;
                            if (!target || typeof target !== 'object' || isArray(target)) {
                                continue;
                            }
                            for (const name of stringKeys(raw)) {
                                const before = descriptor(target, name);
                                if (before && (!('value' in before) || !before.writable)) {
                                    continue;
                                }
                                define(target, name, { value: raw[name], writable: true,
                                    enumerable: true, configurable: true });
                            }
                            continue;
                        }
                        if (operator === 'repl') {
                            const before = descriptor(owner, key)?.value;
                            if (typeof before !== 'string' || before.length > 65536 ||
                                before.length * raw.replacement.length > 1024 * 1024) { continue; }
                            const pattern = raw.regex !== undefined
                                ? new RegExp(raw.regex, raw.flags)
                                : new RegExp(raw.pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
                            value = before.replace(pattern, raw.replacement);
                            if (value.length > 65536) { continue; }
                        }
                        const before = descriptor(owner, key);
                        define(owner, key, before ? { ...before, value } : {
                            value, writable: true, configurable: true, enumerable: true,
                        });
                    } else if (isArray(owner) && typeof key === 'number') {
                        apply(arraySplice, owner, [key, 1]);
                    }
                    else { delete owner[key]; }
                }
                return pending.length ? { value: root.value ?? null } : null;
            } catch { return null; }
        }
        return edit;
    }
    function installJsonEdit() {
        const edits = selected.filter(([type]) =>
            ['JSON_EDIT', 'TRUSTED_JSON_EDIT'].includes(type)).map(([, args]) => ({
            edit: jsonEditor(args), matches: args[3] ? matcher(args[3]) : null,
        }));
        wrap(JSON, 'parse', (target, receiver, args) => {
            let value = apply(target, receiver, args);
            if (typeof args[0] !== 'string' || args[0].length > 1024 * 1024) { return value; }
            const work = { remaining: 65536 };
            for (const entry of edits) {
                if (!entry.matches || entry.matches(args[0])) {
                    const result = entry.edit(value, work);
                    if (result) { value = result.value; }
                }
            }
            return value;
        });
    }
    function installObjectEditing() {
        for (const [type, args] of selected) {
            const element = type === 'TRUSTED_EDIT_INBOUND_ELEMENT';
            const pruning = type.includes('PRUNE_') && type.includes('_OBJECT');
            if ((!type.includes('EDIT_') || !type.includes('_OBJECT')) && !element && !pruning ||
                type.endsWith('EDIT_ELEMENT_OBJECT')) { continue; }
            const kind = type.replace('TRUSTED_', '').replace('PRUNE_', 'EDIT_');
            const prune = pruning ? jsonPruner([[args[2], kind === 'EDIT_INBOUND_OBJECT'
                ? (args[2] + ' ' + args[3]).trim() : args[3]]]) : null;
            const edit = pruning ? value => ({ value: prune(value) })
                : jsonEditor([element ? args[3] : args[2], args[4]]);
            const stack = pruning && args[5] ? matcher(args[5], true) : null;
            const StackError = self.Error;
            const nativeMatches = self.Element?.prototype.matches;
            trap(self, args[0].split('.'), state => {
                const mutate = value => { edit(value); return value; };
                if (kind === 'EDIT_OBJECT_ON_GETTER') {
                    state.readTransforms.push(mutate); return;
                }
                if (kind === 'EDIT_OBJECT_ON_SETTER') {
                    mutate(state.value); state.writeTransforms.push(mutate); return;
                }
                const proxies = new WeakMap();
                state.readTransforms.push(value => {
                    if (typeof value !== 'function') { return value; }
                    if (proxies.has(value)) { return proxies.get(value); }
                    let active = false;
                    const proxy = new Proxy(value, { apply(target, receiver, values) {
                        if (active) { return apply(target, receiver, values); }
                        active = true;
                        try {
                            if (stack && !stack(new StackError().stack || '')) {
                                return apply(target, receiver, values);
                            }
                            if (element) {
                                const position = Number(args[1]);
                                const index = position < 0 ? values.length + position : position;
                                const node = args[1] === 'this' ? receiver : values[index];
                                if (self.Element && node instanceof self.Element &&
                                    apply(nativeMatches, node, [args[2]])) { edit(node); }
                            }
                            if (kind === 'EDIT_INBOUND_OBJECT') {
                                const position = Number(args[1]) - (pruning ? 1 : 0);
                                const index = position < 0 ? values.length + position : position;
                                if (index >= 0 && index < values.length) {
                                    if (pruning && !prune(values[index], true)) {
                                        return apply(target, receiver, values);
                                    }
                                    if (pruning && args[4] !== '1') { edit(values[index]); }
                                    else {
                                    try {
                                        const text = nativeStringify(values[index]);
                                        if (typeof text === 'string' &&
                                            text.length <= 1024 * 1024) {
                                            const copy = nativeParse(text);
                                            const result = edit(copy);
                                            if (result) { values[index] = result.value; }
                                        }
                                    } catch {}
                                    }
                                }
                            }
                            if (kind === 'EDIT_THIS_OBJECT' && args[3] !== 'after') {
                                edit(receiver);
                            }
                            let result = apply(target, receiver, values);
                            if (kind === 'EDIT_THIS_OBJECT' && args[3] === 'after') {
                                edit(receiver);
                            }
                            if (kind === 'EDIT_OUTBOUND_OBJECT') {
                                const edited = edit(result);
                                if (edited) { result = edited.value; }
                            }
                            return result;
                        } finally { active = false; }
                    } });
                    proxies.set(value, proxy); return proxy;
                });
            });
        }
    }
    function installJson() {
        const prune = jsonPruner(groups.get('JSON_PRUNE'));
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
        const tests = selected.filter(rule =>
            ['PREVENT_FETCH', 'TRUSTED_PREVENT_FETCH'].includes(rule[0])).map(([type, args]) => ({
            fields: args[0].split(' ').map(part => {
                const colon = part.indexOf(':');
                const name = part.slice(0, colon);
                return colon > 0 && (name === 'url' || name === 'method')
                    ? [name, matcher(part.slice(colon + 1), true)]
                    : ['url', matcher(part, true)];
            }),
            body: args[1] === 'emptyObj' ? '{}' : args[1] === 'emptyArr' ? '[]'
                : type === 'TRUSTED_PREVENT_FETCH' && !['', 'emptyStr'].includes(args[1])
                    ? args[1] : '',

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
        ['JSON_PRUNE', installJson], ['JSON_EDIT', installJsonEdit],
        ['TRUSTED_JSON_EDIT', installJsonEdit],
        ['PREVENT_FETCH', installFetch], ['TRUSTED_PREVENT_FETCH', installFetch],
    ]);
    for (const [type] of selected) {
        if (type.includes('EDIT_') && type.includes('_OBJECT') ||
            type === 'TRUSTED_EDIT_INBOUND_ELEMENT' || type.includes('PRUNE_') &&
            type.includes('_OBJECT')) {
            installers.set(type, installObjectEditing);
        }
    }
    const environment = {
        groups, selected, wrap, apply, define, descriptor, matcher, source, nativeAdd, regexpTest,
        trap, abort, resources: resources || {}, jsonPruner, jsonEditor,
        nativeThen, resolved, rejected, NativeResponse, NativeRequest,
    };
    const extra = modules.has(1) && typeof extension === 'function'
        ? extension(environment) : new Map();
    const advancedHooks = modules.has(3) && typeof advanced === 'function'
        ? advanced(environment) : new Map();
    const responses = modules.has(2) && typeof network === 'function'
        ? network(environment) : new Map();
    for (const [type, installer] of [...extra, ...advancedHooks, ...responses]) {
        installers.set(type, installer);
    }
    const installed = new Set();
    for (const [type, args] of selected) {
        const install = installers.get(type);
        if (!install) { installProperty(type, args); }
        else if (type === 'RESOURCE' || type === 'TRUSTED_RESOURCE' ||
            type === 'SET_LOCAL_STORAGE' ||
            type === 'SET_SESSION_STORAGE' || type === 'SET_COOKIE' ||
            type === 'SET_COOKIE_RELOAD' || type === 'REMOVE_COOKIE' ||
            type === 'CALL_NOTHROW' || type === 'ABORT_STACK_TRACE' ||
            ['TRUSTED_LOCAL_STORAGE', 'TRUSTED_SESSION_STORAGE',
                'TRUSTED_SET_COOKIE', 'TRUSTED_SET_COOKIE_RELOAD'].includes(type)) {
            try { install(args); } catch {}
        } else if (!installed.has(install)) {
            installed.add(install);
            try { install(); } catch {}
        }
    }
    installing = false;

})
