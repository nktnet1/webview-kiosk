/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Adapted from uBlock Origin, Copyright (C) 2019-2026 Raymond Hill and contributors.
 * Modified for Webview Kiosk on 2026-10-17: bounded trusted hooks and DOM work.
 * Source attribution and licence: META-INF/ublock-NOTICE.txt.
 */
(function(env) {
    'use strict';
    const { selected, wrap, apply, descriptor, matcher, nativeAdd, resolved } = env;
    const parse = JSON.parse;
    const timeout = self.setTimeout.bind(self);
    const clear = self.clearTimeout.bind(self);
    const now = Date.now.bind(Date);
    const NativeError = self.ReferenceError;
    const NativeParser = self.DOMParser;
    const NativeElement = self.HTMLElement;
    const nativeClick = NativeElement?.prototype.click;
    const nativeMatches = self.Element?.prototype.matches;
    const nativeAtob = self.atob?.bind(self);
    const nativeBtoa = self.btoa?.bind(self);
    const textAccess = self.Node && descriptor(self.Node.prototype, 'textContent');
    const scriptTextAccess = self.HTMLScriptElement &&
        descriptor(self.HTMLScriptElement.prototype, 'textContent');
    const documentQuery = self.Document?.prototype.querySelector;
    const documentQueryAll = self.Document?.prototype.querySelectorAll;
    const fragmentQuery = self.DocumentFragment?.prototype.querySelector;
    const stringReplace = String.prototype.replace;
    const rules = type => selected.filter(rule => rule[0] === type).slice(0, 32)
        .map(rule => rule[1]);
    const options = (args, offset) => Object.fromEntries(
        args.slice(offset).reduce((out, value, index, all) => {
            if (index % 2 === 0) { out.push([value, all[index + 1]]); }
            return out;
        }, []));
    const listen = (owner, name, fn) => apply(nativeAdd, owner, [name, fn]);
    const primitive = value => value == null ||
        ['string', 'number', 'boolean'].includes(typeof value);
    function constant(raw) {
        if (raw.startsWith('json:')) { return apply(parse, JSON, [raw.slice(5)]); }
        if (raw.startsWith('{') && raw !== '{}') {
            return apply(parse, JSON, [raw]).value;
        }
        switch (raw) {
        case 'undefined': return undefined;
        case 'null': return null;
        case 'true': return true;
        case 'false': return false;
        case '': case "''": return '';
        case '[]': case 'emptyArr': return [];
        case '{}': case 'emptyObj': return {};
        case 'noopFunc': return function() {};
        case 'trueFunc': return function() { return true; };
        case 'falseFunc': return function() { return false; };
        case 'throwFunc': return function() { throw ''; };
        default: return Number(raw);
        }
    }
    function hook(path, callback) {
        const parts = path.split('.');
        let owner = self;
        for (const part of parts.slice(0, -1)) { owner = owner?.[part]; }
        wrap(owner, parts.at(-1), callback);
    }
    function replacer(pattern, replacement, all = false) {
        const end = pattern.lastIndexOf('/');
        const escaped = apply(stringReplace, pattern, [/[$()*+.?[\]^{|}\\]/g, '\\$&']);
        const regex = pattern.startsWith('/') && end > 0 &&
            /^[dgimsuvy]*$/.test(pattern.slice(end + 1))
            ? new RegExp(pattern.slice(1, end), pattern.slice(end + 1))
            : new RegExp(escaped, all ? 'g' : '');
        function expand(match, groups, offset, input) {
            const pieces = []; let size = 0;
            for (let i = 0; i < replacement.length; i++) {
                let part = replacement[i];
                if (part === '$' && i + 1 < replacement.length) {
                    const next = replacement[i + 1];
                    if (next === '$') { part = '$'; i++; }
                    else if (next === '&') { part = match; i++; }
                    else if (next === '`') { part = input.slice(0, offset); i++; }
                    else if (next === "'") { part = input.slice(offset + match.length); i++; }
                    else if (/\d/.test(next)) {
                        const digits = /^\d{1,2}/.exec(replacement.slice(i + 1))[0];
                        let count = digits.length; let number = Number(digits);
                        if (number > groups.length && count === 2) {
                            number = Number(digits[0]); count = 1;
                        }
                        if (number > 0 && number <= groups.length) {
                            part = groups[number - 1] || ''; i += count;
                        }
                    }
                }
                size += part.length;
                if (size > 65536) { throw new RangeError('Replacement exceeds the text budget'); }
                pieces.push(part);
            }
            return pieces.join('');
        }
        return value => {
            if (typeof value !== 'string' || value.length > 65536) { return value; }
            let size = value.length; let count = 0;
            try {
                regex.lastIndex = 0;
                const after = apply(stringReplace, value, [regex, (match, ...args) => {
                    const offset = args.at(-2);
                    const after = expand(match, args.slice(0, -2), offset, value);
                    size += after.length - match.length;
                    if (++count > 1024 || size > 65536) {
                        throw new RangeError('Replacement exceeds the text budget');
                    }
                    return after;
                }]);
                return typeof after === 'string' && after.length <= 65536 ? after : value;
            } catch { return value; }
        };
    }
    function replaceArguments() {
        for (const args of rules('TRUSTED_REPLACE_ARGUMENT')) {
            const extra = options(args, 3);
            const condition = extra.condition === undefined ? null : matcher(extra.condition);
            let replace;
            if (args[2].startsWith('repl:/')) {
                const raw = args[2].slice(5); const fields = []; let start = 0; let escaped = false;
                for (let index = 0; index < raw.length; index++) {
                    if (raw[index] === '/' && !escaped) {
                        fields.push(raw.slice(start, index)); start = index + 1;
                    }
                    escaped = raw[index] === '\\' && !escaped;
                }
                fields.push(raw.slice(start));
                replace = replacer('/' + fields[1] + '/' + fields[3],
                    fields[2].replace(/\\\//g, '/'));
            } else if (args[2].startsWith('add:')) {
                const delta = Number(args[2].slice(4));
                replace = value => primitive(value) && Number.isFinite(Number(value) + delta)
                    ? Number(value) + delta : value;
            } else {
                const value = constant(args[2]); replace = () => value;
            }
            hook(args[0], (target, receiver, values) => {
                const raw = Number(args[1]);
                const index = raw < 0 ? values.length + raw : raw;
                if (args[1] !== 'this' && (index < 0 || index >= values.length)) {
                    return apply(target, receiver, values);
                }
                const before = args[1] === 'this' ? receiver : values[index];
                if (condition && (!primitive(before) || !condition(String(before)))) {
                    return apply(target, receiver, values);
                }
                const after = replace(before);
                if (args[1] === 'this') { receiver = after; }
                else { values[index] = after; }
                return apply(target, receiver, values);
            });
        }
    }
    function replaceOutbound() {
        for (const args of rules('TRUSTED_REPLACE_OUTBOUND_TEXT')) {
            const extra = options(args, 3);
            const condition = matcher(extra.condition || '');
            const replace = replacer(args[1], args[2].startsWith('json:')
                ? apply(parse, JSON, [args[2].slice(5)]) : args[2]);
            hook(args[0], (target, receiver, values) => {
                const original = apply(target, receiver, values);
                if (typeof original !== 'string' || original.length > 65536) { return original; }
                try {
                    const before = extra.encoding === 'base64' ? nativeAtob(original) : original;
                    if (!condition(before)) { return original; }
                    const after = replace(before);
                    if (after === before) { return original; }
                    return extra.encoding === 'base64' ? nativeBtoa(after) : after;
                } catch { return original; }
            });
        }
    }
    function suppress() {
        for (const args of rules('TRUSTED_SUPPRESS_NATIVE_METHOD')) {
            const tests = args[1].split('|').map(raw => {
                const value = raw.trim();
                if (value === '*') { return () => true; }
                if (value.startsWith('"') || value.startsWith('/')) {
                    const test = matcher(value.startsWith('"') ? value.slice(1, -1) : value);
                    return arg => primitive(arg) && test(String(arg));
                }
                const expected = constant(value); return arg => arg === expected;
            });
            const stack = args[3] ? matcher(args[3], true) : null;
            hook(args[0], (target, receiver, values) => {
                if (!tests.every((test, index) => test(values[index])) ||
                    stack && !stack(new NativeError().stack || '')) {
                    return apply(target, receiver, values);
                }
                if (args[2] === 'abort') { throw new NativeError(); }
            });
        }
    }
    function overrideElements() {
        for (const args of rules('TRUSTED_OVERRIDE_ELEMENT_METHOD')) {
            hook(args[0], (target, receiver, values) => {
                let match = args[1] === '';
                try { match ||= Boolean(apply(nativeMatches, receiver, [args[1]])); } catch {}
                if (!match) { return apply(target, receiver, values); }
                if (args[2] === 'throw') { throw new NativeError(); }
                return args[2] ? constant(args[2]) : undefined;
            });
        }
    }
    function bypass() {
        for (const args of rules('TRUSTED_PREVENT_DOM_BYPASS')) {
            hook(args[0], (target, receiver, values) => {
                const result = apply(target, receiver, values);
                for (const node of values.slice(0, 16)) {
                    if (!NativeElement || !(node instanceof NativeElement)) { continue; }
                    try {
                        let child = node.contentWindow; let parent = self;
                        const href = child?.location.href;
                        if (href !== 'about:blank' && href !== location.href) { continue; }
                        const parts = args[1].split('.');
                        for (const part of parts.slice(0, -1)) {
                            child = child[part]; parent = parent[part];
                        }
                        child[parts.at(-1)] = parent[parts.at(-1)];
                    } catch {}
                }
                return result;
            });
        }
    }
    function clipboard() {
        const entries = rules('PREVENT_CLIPBOARD_WRITE').map(args => {
            const extra = options(args, 1);
            return { match: matcher(args[0]), exclude: extra.excludeMatches
                ? matcher(extra.excludeMatches) : null };
        });
        const prevent = text => typeof text === 'string' && entries.some(entry =>
            entry.match(text.trim()) && (!entry.exclude || !entry.exclude(text.trim())));
        wrap(self.navigator?.clipboard, 'writeText', (target, receiver, args) =>
            prevent(args[0]) ? resolved(undefined) : apply(target, receiver, args));
        wrap(document, 'execCommand', (target, receiver, args) => {
            if (['copy', 'cut'].includes(args[0]) &&
                prevent(document.getSelection()?.toString())) { return true; }
            return apply(target, receiver, args);
        });
    }
    function assertions(raw) {
        if (!raw) { return true; }
        try {
            return raw.split(',').every(value => {
                const [kind, ...rest] = value.split(':');
                const pattern = rest.join(':').trim();
                const not = kind.trim().startsWith('!');
                const type = kind.trim().replace(/^!/, '');
                const items = type === 'cookie' ? document.cookie.split(';').map(s => s.trim())
                    : Object.keys(self.localStorage).slice(0, 1024)
                        .map(key => key + '=' + self.localStorage.getItem(key));
                const test = pattern.startsWith('/') ? matcher(pattern)
                    : text => text.startsWith(pattern.includes('=') ? pattern : pattern + '=');
                return items.some(test) !== not;
            });
        } catch { return false; }
    }
    function dom() {
        if (!self.MutationObserver) { return; }
        const types = ['TRUSTED_REPLACE_NODE_TEXT', 'TRUSTED_CLICK_ELEMENT', 'TRUSTED_CREATE_HTML'];
        const entries = selected.filter(rule => types.includes(rule[0])).slice(0, 32)
            .map(([type, args]) => {
                const extra = options(args, 3);
                const entry = { type, args, extra, done: false, remaining: 1024,
                    seen: new WeakMap() };
                if (type === 'TRUSTED_REPLACE_NODE_TEXT') {
                    entry.name = args[0].startsWith('/') ? matcher(args[0])
                        : name => name.toLowerCase() === args[0].toLowerCase();
                    entry.replace = replacer(args[1], args[2], true);
                    entry.include = matcher(extra.includes || extra.condition || '');
                    entry.exclude = extra.excludes ? matcher(extra.excludes) : null;
                    entry.remaining = Number(extra.sedCount || 1024);
                } else if (type === 'TRUSTED_CLICK_ELEMENT') {
                    const separator = /^[;|]/.test(args[0]) ? args[0][0] : ',';
                    entry.steps = (separator === ',' ? args[0] : args[0].slice(1))
                        .split(separator).map(s => /^\d+$/.test(s.trim()) ? Number(s) : s.trim());
                    entry.until = now() + (typeof entry.steps.at(-1) === 'number'
                        ? entry.steps.pop() : 11000);
                    entry.wait = entry.steps.length === 1 ? now() + Number(args[2] || 0) : 0;
                    entry.done = !assertions(args[1]);
                } else { entry.done = self !== self.top; }
                return entry;
            });
        let stopped = false; let queued = false;
        const timers = new Set();
        const observed = new WeakSet();
        let observedCount = 1;
        const mutationOptions = { childList: true, subtree: true, attributes: true,
            attributeFilter: ['class', 'id', 'style', 'hidden', 'open'] };
        function later(callback, delay) {
            const id = timeout(() => { timers.delete(id); if (!stopped) { callback(); } }, delay);
            timers.add(id);
        }
        function stop() {
            stopped = true; observer.disconnect();
            for (const id of timers) { clear(id); } timers.clear();
        }
        function text(root, budget) {
            function visit(node) {
                if (--budget.value < 0) { return false; }
                if (node === document.currentScript || !textAccess?.get || !textAccess.set) {
                    return true;
                }
                for (const entry of entries) {
                    if (entry.done || entry.type !== 'TRUSTED_REPLACE_NODE_TEXT' ||
                        !entry.name(node.nodeName)) { continue; }
                    const before = apply(textAccess.get, node, []);
                    if (typeof before !== 'string' || entry.seen.get(node) === before) { continue; }
                    budget.text -= Math.min(before.length, 65536);
                    if (budget.text < 0) { budget.value = -1; return false; }
                    if (before.length > 65536 ||
                        !entry.include(before) ||
                        entry.exclude?.(before)) { continue; }
                    const after = entry.args[1] === '' ? entry.args[2] : entry.replace(before);
                    if (after === before || after.length > 65536) { continue; }
                    budget.text -= after.length;
                    if (budget.text < 0) { budget.value = -1; return false; }
                    const setter = node.nodeName === 'SCRIPT' && scriptTextAccess?.set
                        ? scriptTextAccess.set : textAccess.set;
                    try { apply(setter, node, [after]); } catch { continue; }
                    entry.seen.set(node, after);
                    if (--entry.remaining === 0) { entry.done = true; }
                }
                return true;
            }
            if (!visit(root) || !document.createTreeWalker) { return; }
            const walker = document.createTreeWalker(root, 1 | 4); let node;
            while ((node = walker.nextNode()) && visit(node)) {}
        }
        function lookup(raw) {
            let root = document;
            const parts = raw.split('>>>').map(s => s.trim());
            for (const part of parts.slice(0, -1)) {
                const query = root === document ? documentQuery : fragmentQuery;
                root = apply(query, root, [part])?.shadowRoot;
                if (!root) { return null; }
                if (!observed.has(root)) {
                    if (observedCount >= 128) { return null; }
                    observedCount++;
                    observer.observe(root, mutationOptions); observed.add(root);
                }
            }
            return apply(root === document ? documentQuery : fragmentQuery, root,
                [parts.at(-1)]);
        }
        function sweep() {
            queued = false;
            if (stopped) { return; }
            for (const entry of entries) {
                if (entry.done || entry.type === 'TRUSTED_REPLACE_NODE_TEXT') { continue; }
                try {
                    const { args, extra } = entry;
                    if (entry.type === 'TRUSTED_CLICK_ELEMENT') {
                        if (now() >= entry.until) { entry.done = true; continue; }
                        if (now() < entry.wait) { continue; }
                        const step = entry.steps[0];
                        if (typeof step === 'number') {
                            entry.steps.shift(); entry.wait = now() + step;
                            later(sweep, step); continue;
                        }
                        const node = lookup(step);
                        if (!node || !nativeClick) { continue; }
                        // Pop the step before clicking: handlers may mutate the DOM synchronously.
                        entry.steps.shift();
                        apply(nativeClick, node, []);
                        entry.done = entry.steps.length === 0;
                        entry.wait = now() + Number(args[2] || 1);
                        if (!entry.done) { later(sweep, Number(args[2] || 1)); }
                    } else {
                        if (!NativeParser || extra.runAt === 'complete' &&
                            document.readyState !== 'complete' ||
                            extra.runAt === 'interactive' && document.readyState === 'loading') {
                            continue;
                        }
                        const parents = apply(documentQueryAll, document, [args[0]]);
                        if (!parents.length) { continue; }
                        const parsed = new NativeParser().parseFromString(args[1], 'text/html');
                        if (!parsed.body || parsed.querySelectorAll('*').length > 64) {
                            entry.done = true; continue;
                        }
                        const inserted = [];
                        const nodes = Array.from(parsed.body.childNodes);
                        entry.done = true;
                        const targets = Array.from(parents).slice(0, Number(extra.limit || 1));
                        for (const parent of targets) {
                            for (const node of nodes) {
                                const copy = document.importNode(node, true);
                                parent.appendChild(copy); inserted.push(copy);
                            }
                        }
                        if (args[2] !== '') {
                            // Removal still runs after observers have finished.
                            timeout(() => { for (const node of inserted) { node.remove(); } },
                                Number(args[2]));
                        }
                    }
                } catch { entry.done = true; }
            }
            if (entries.every(entry => entry.done)) { stop(); }
        }
        function schedule() {
            if (queued || stopped) { return; }
            queued = true; later(sweep, 0);
        }
        const observer = new self.MutationObserver(records => {
            const budget = { value: 4096, text: 4 * 1024 * 1024 };
            for (const record of records) {
                for (const node of record.addedNodes) {
                    if (budget.value < 0) { break; }
                    text(node, budget);
                }
                if (budget.value < 0) { break; }
            }
            schedule();
        });
        observer.observe(document, mutationOptions); observed.add(document);
        listen(document, 'readystatechange', () => {
            if (document.readyState !== 'loading') {
                for (const entry of entries) {
                    if (entry.type !== 'TRUSTED_REPLACE_NODE_TEXT' || entry.done) { continue; }
                    if (entry.extra.quitAfter) {
                        later(() => { entry.done = true; sweep(); }, Number(entry.extra.quitAfter));
                    } else { entry.done = true; }
                }
            }
            sweep();
        });
        listen(self, 'pagehide', stop);
        for (const entry of entries) {
            if (entry.type === 'TRUSTED_CLICK_ELEMENT') {
                later(sweep, Math.max(0, entry.wait - now()));
                later(() => { entry.done = true; sweep(); }, Math.max(0, entry.until - now()));
            }
        }
        later(stop, 30000);
        text(document, { value: 4096, text: 4 * 1024 * 1024 }); sweep();
    }
    return new Map([
        ['TRUSTED_REPLACE_ARGUMENT', replaceArguments],
        ['TRUSTED_REPLACE_OUTBOUND_TEXT', replaceOutbound],
        ['TRUSTED_SUPPRESS_NATIVE_METHOD', suppress],
        ['TRUSTED_OVERRIDE_ELEMENT_METHOD', overrideElements],
        ['TRUSTED_PREVENT_DOM_BYPASS', bypass], ['PREVENT_CLIPBOARD_WRITE', clipboard],
        ['TRUSTED_REPLACE_NODE_TEXT', dom], ['TRUSTED_CLICK_ELEMENT', dom],
        ['TRUSTED_CREATE_HTML', dom],
    ]);
})
