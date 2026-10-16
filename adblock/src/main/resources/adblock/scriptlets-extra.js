/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Adapted from uBlock Origin, Copyright (C) 2019-2026 Raymond Hill and contributors.
 * Modified for Webview Kiosk on 2026-10-16: validated subsets, bounded work, shared hooks.
 * Source attribution and licence: META-INF/ublock-NOTICE.txt.
 */
(function(env) {
    'use strict';
    const { groups, selected, wrap, apply, define, descriptor, matcher, source,
        nativeAdd, trap, abort, resources } = env;
    const timeout = self.setTimeout.bind(self);
    const clearTimeout = self.clearTimeout.bind(self);
    const frame = typeof self.requestAnimationFrame === 'function'
        ? self.requestAnimationFrame.bind(self) : null;
    const cancelFrame = typeof self.cancelAnimationFrame === 'function'
        ? self.cancelAnimationFrame.bind(self) : null;
    const NativeStackError = self.Error;
    const rules = type => groups.get(type) || [];
    const listen = (owner, name, fn, options) => apply(nativeAdd, owner, [name, fn, options]);
    const range = raw => {
        if (!raw) { return () => true; }
        const not = raw.startsWith('!');
        const bounds = (not ? raw.slice(1) : raw).split('-');
        const low = Number(bounds[0] || 0);
        const high = bounds.length === 1 ? low : Number(bounds[1] || Infinity);
        return value => (value >= low && value <= high) !== not;
    };
    function timers(name, defuse, boost) {
        const entries = selected.filter(rule => [defuse, boost].includes(rule[0]))
            .reverse().map(([type, args]) => ({
                defuse: type === defuse,
                match: matcher(args[0], type === defuse),
                any: args[0] === '',
                delay: range(args[1]),
                exact: args[1] === '*' ? null : Number(args[1] || 1000),
                ratio: args[2] === '' || args[2] === undefined ? 0.05
                    : Math.min(50, Math.max(0.001, Number(args[2]))),
            }));
        const bab = name === 'setTimeout' && groups.has('PREVENT_BAB');
        wrap(self, name, (target, receiver, args) => {
            if ((typeof args[0] !== 'function' && typeof args[0] !== 'string') ||
                (args[1] !== undefined && !['string', 'number'].includes(typeof args[1]))) {
                return apply(target, receiver, args);
            }
            let text;
            const callback = () => text === undefined ? (text = source(args[0])) : text;
            if (bab && typeof args[0] === 'string' &&
                /\.bab_elementid.$/.test(args[0])) {
                args[0] = function() {};
            }
            for (const entry of entries) {
                if (entry.defuse) {
                    const delay = Math.max(0, Number(args[1]) || 0);
                    if (entry.delay(delay) && (entry.any || entry.match(callback()))) {
                        args[0] = function() {};
                        text = undefined;
                    }
                } else if ((entry.exact === null || args[1] === entry.exact) &&
                    (entry.any || entry.match(callback()))) {
                    args[1] *= entry.ratio;
                }
            }
            return apply(target, receiver, args);
        });
    }
    const installTimeout = () => timers('setTimeout', 'PREVENT_SET_TIMEOUT', 'ADJUST_SET_TIMEOUT');
    const installInterval = () =>
        timers('setInterval', 'PREVENT_SET_INTERVAL', 'ADJUST_SET_INTERVAL');
    function animation() {
        const tests = rules('PREVENT_ANIMATION_FRAME').map(args => matcher(args[0], true));
        wrap(self, 'requestAnimationFrame', (target, receiver, args) => {
            if (typeof args[0] === 'function' && tests.some(test => test(source(args[0])))) {
                args[0] = function() {};
            }
            return apply(target, receiver, args);
        });
    }
    function babCheck(text) {
        const signatures = [ ['blockadblock'], ['babasbm'], ["getItem('babn')"],
            ['getElementById', 'String.fromCharCode',
                'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789',
                'charAt', 'DOMContentLoaded', 'AdBlock', 'addEventListener', 'doScroll',
                'fromCharCode', '<<2|r>>4', 'sessionStorage', 'clientWidth', 'localStorage',
                'Math', 'random'] ];
        return signatures.some(tokens =>
            tokens.filter(token => text.includes(token)).length / tokens.length >= 0.8);
    }
    function evaluation() {
        const tests = rules('PREVENT_EVAL').map(args => ({
            all: args[0] === '', match: matcher(args[0]),
        }));
        const bab = groups.has('PREVENT_BAB');
        wrap(self, 'eval', (target, receiver, args) => {
            const text = source(args[0]);
            if (tests.some(test => test.all || test.match(text))) { return; }
            if (bab && typeof text === 'string' && babCheck(text)) {
                document.body?.style?.removeProperty('visibility');
                document.getElementById?.('babasbmsgx')?.remove();
                return;
            }
            return apply(target, receiver, args);
        });
    }
    function stackAbort(args) {
        const match = matcher(args[1], true);
        const docURL = new URL(location.href); docURL.hash = '';
        const stackToken = 'wk-stack-' + Math.random().toString(36).slice(2);
        const check = () => {
            const stack = new NativeStackError(stackToken).stack;
            if (typeof stack !== 'string' || stack.length > 65536) { return; }
            const lines = [];
            const reLine = /(.*?@)?(\S+)(:\d+):\d+\)?$/;
            for (let line of stack.split(/[\n\r]+/).slice(0, 65)) {
                if (line.length > 1024 || line.includes(stackToken)) { continue; }
                line = line.trim();
                const found = reLine.exec(line);
                if (!found) { continue; }
                let url = found[2];
                if (url.startsWith('(')) { url = url.slice(1); }
                if (url === docURL.href) { url = 'inlineScript'; }
                else if (url.startsWith('<anonymous>')) { url = 'injectedScript'; }
                let fn = found[1] !== undefined ? found[1].slice(0, -1)
                    : line.slice(0, found.index).trim();
                if (fn.startsWith('at')) { fn = fn.slice(2).trim(); }
                lines.push(' ' + (fn + ' ' + url + found[3] + ':1').trim());
            }
            lines[0] = 'stackDepth:' + (lines.length - 1);
            if (match(lines.join('\t'))) { abort(); }
        };
        trap(self, args[0].split('.'), state => {
            if (state.mode && state.mode !== 'ABORT_STACK_TRACE') { return; }
            state.mode = 'ABORT_STACK_TRACE';
            state.readers.push(check);
        });
    }
    function storage(kind, args) {
        const store = self[kind];
        if (args[1] !== '$remove$') { store.setItem(args[0], args[1]); return; }
        if (args[0].startsWith('/') && args[0].lastIndexOf('/') > 0) {
            if (store.length > 1024) { return; }
            const match = matcher(args[0]);
            const keys = Array.from({ length: store.length }, (_, i) => store.key(i));
            for (const key of keys) { if (match(key)) { store.removeItem(key); } }
        } else { store.removeItem(args[0]); }
    }
    function cookie(args, reload) {
        const name = encodeURIComponent(args[0]);
        const value = encodeURIComponent(args[1]);
        const wanted = name + '=' + value;
        const present = () => document.cookie.split(/\s*;\s*/).includes(wanted);
        if (present()) { return; }
        let text = wanted;
        if (args[2] !== 'none') { text += '; path=/'; }
        if (name.startsWith('__Secure-') || name.startsWith('__Host-')) { text += '; Secure'; }
        document.cookie = text;
        if (reload && present()) { location.reload(); }
    }
    function removeCookie(args) {
        const match = matcher(args[0]);
        const remove = () => {
            const cookies = document.cookie.split(/\s*;\s*/);
            if (cookies.length > 256) { return; }
            for (const cookie of cookies) {
                const name = cookie.split('=')[0];
                if (!match(name)) { continue; }
                const domains = new Set(['', location.hostname, '.' + location.hostname]);
                if (document.domain) {
                    domains.add(document.domain);
                    domains.add('.' + document.domain);
                    if (document.domain.startsWith('www.')) {
                        domains.add(document.domain.slice(4));
                    }
                }
                for (const domain of domains) {
                    const base = name + '=; Max-Age=-1000; expires=Thu, 01 Jan 1970 00:00:00 GMT';
                    const suffix = domain ? '; domain=' + domain : '';
                    document.cookie = base + suffix;
                    document.cookie = base + suffix + '; path=/';
                }
            }
        };
        remove();
        listen(self, 'beforeunload', remove);
    }
    function noThrow(args) {
        const parts = args[0].split('.');
        let owner = self;
        for (const part of parts.slice(0, -1)) {
            owner = owner[part];
            if (!owner) { return; }
        }
        wrap(owner, parts.at(-1), (target, receiver, values) => {
            try { return apply(target, receiver, values); } catch {}
        });
    }
    function canvas() {
        const tests = rules('PREVENT_CANVAS').map(args => matcher(args[0], true));
        if (!self.HTMLCanvasElement) { return; }
        wrap(HTMLCanvasElement.prototype, 'getContext', (target, receiver, args) => {
            if (typeof args[0] === 'string' && tests.some(test => test(args[0]))) { return null; }
            return apply(target, receiver, args);
        });
    }
    function freeze() {
        const properties = new Map();
        for (const args of rules('PREVENT_ELEMENT_PROPERTY')) {
            if (!properties.has(args[0])) { properties.set(args[0], []); }
            properties.get(args[0]).push({ selector: args[1], match: matcher(args[2], true) });
        }
        for (const [name, tests] of properties) {
            let owner = name === 'text' || name === 'src' ? self.HTMLScriptElement?.prototype
                : name === 'textContent' ? self.Node?.prototype : self.Element?.prototype;
            while (owner && !descriptor(owner, name)) { owner = Object.getPrototypeOf(owner); }
            const desc = owner && descriptor(owner, name);
            if (!desc?.configurable || !desc.set) { continue; }
            define(owner, name, { ...desc, set(value) {
                if (typeof value === 'string') {
                    for (const test of tests) {
                        try {
                            if ((!test.selector || this.matches(test.selector)) &&
                                test.match(value)) { return; }
                        } catch {}
                    }
                }
                return apply(desc.set, this, [value]);
            } });
        }
    }
    function spoof() {
        const tests = rules('SPOOF_CSS').slice(0, 16).map(args => ({
            selector: args[0], values: new Map(Array.from({ length: (args.length - 1) / 2 },
                (_, i) => [args[i * 2 + 1], args[i * 2 + 2]])),
        }));
        const matching = element => tests.filter(test => {
            try { return element.matches(test.selector); } catch { return false; }
        });
        wrap(self, 'getComputedStyle', (target, receiver, args) => {
            const style = apply(target, receiver, args);
            const values = new Map();
            for (const test of matching(args[0])) {
                for (const entry of test.values) { values.set(...entry); }
            }
            if (values.size === 0) { return style; }
            return new Proxy(style, { get(owner, name) {
                if (values.has(name)) { return values.get(name); }
                if (name === 'getPropertyValue') {
                    return prop => values.has(prop)
                        ? values.get(prop) : owner.getPropertyValue(prop);
                }
                const value = Reflect.get(owner, name, owner);
                return typeof value === 'function' ? value.bind(owner) : value;
            } });
        });
        if (!self.Element || !self.DOMRect) { return; }
        wrap(Element.prototype, 'getBoundingClientRect', (target, receiver, args) => {
            const rect = apply(target, receiver, args);
            let { x, y, width, height } = rect;
            const matches = matching(receiver);
            if (matches.length === 0) { return rect; }
            for (const test of matches) {
                const val = (key, fallback) => test.values.has(key)
                    ? parseFloat(test.values.get(key)) : fallback;
                x = val('_rectx', x); y = val('_recty', y);
                width = val('_rectw', val('width', width));
                height = val('_recth', val('height', height));
            }
            return new DOMRect(x, y, width, height);
        });
    }
    function rtc() {
        const name = self.RTCPeerConnection ? 'RTCPeerConnection' : 'webkitRTCPeerConnection';
        const Native = self[name];
        if (typeof Native !== 'function') { return; }
        if (groups.has('PREVENT_WEBRTC')) {
            const stub = function() {};
            stub.prototype.createDataChannel = () => ({ close() {}, send() {} });
            stub.prototype.createOffer = function() {};
            stub.prototype.setRemoteDescription = function() {};
            self[name] = stub;
            return;
        }
        const tests = rules('WEBRTC_IF').map(args => matcher(args[0]));
        const neutered = new WeakSet();
        if (Native.prototype) {
            wrap(Native.prototype, 'createDataChannel', (target, receiver, args) => {
                if (neutered.has(receiver)) { args[1] = {}; }
                return apply(target, receiver, args);
            });
        }
        self[name] = new Proxy(Native, { construct(target, args, ctor) {
            let block = false;
            try {
                const servers = args[0]?.iceServers;
                if (Array.isArray(servers) && servers.length <= 64) {
                    const values = [];
                    for (const server of servers) {
                        const urls = server.urls || server.url;
                        if (typeof urls === 'string') { values.push(urls); }
                        else if (Array.isArray(urls) && urls.length <= 64 &&
                            urls.every(url => typeof url === 'string')) { values.push(...urls); }
                        if (typeof server.username === 'string') { values.push(server.username); }
                        if (typeof server.credential === 'string') {
                            values.push(server.credential);
                        }
                    }
                    block = !tests.every(test => values.some(test));
                }
            } catch { /* Native validation handles malformed configurations. */ }
            if (block) { args[0] = undefined; }
            const result = Reflect.construct(target, args, ctor);
            if (block) { neutered.add(result); }
            return result;
        } });
    }
    function newtabs() {
        listen(document, 'click', event => {
            let node = event.target;
            for (let depth = 0; node && depth < 128; depth++, node = node.parentNode) {
                if (node.localName === 'a' && node.hasAttribute('target')) {
                    node.removeAttribute('target'); event.preventDefault(); break;
                }
            }
        });
    }
    function navigation() {
        if (!self.navigation) { return; }
        const tests = rules('PREVENT_NAVIGATION').map(args =>
            matcher(args[0] === 'location.href' ? location.href : args[0], true));
        listen(self.navigation, 'navigate', event => {
            if (!event.userInitiated && event.cancelable &&
                tests.some(test => test(event.destination?.url))) { event.preventDefault(); }
        });
    }
    function refresh() {
        const stop = self.stop?.bind(self);
        if (!stop) { return; }
        const run = () => {
            const nodes = document.querySelectorAll('meta[http-equiv="refresh" i]');
            if (nodes.length === 0) { return; }
            const delays = Array.from(nodes).slice(0, 64).map(node =>
                Number.parseFloat(node.getAttribute('content'))).filter(Number.isFinite);
            if (delays.length === 0) { return; }
            const force = rules('PREVENT_REFRESH').some(args => args[0] !== '');
            const delay = force ? 0 : Math.min(86400000, Math.max(0, Math.min(...delays) * 500));
            if (delay === 0) { stop(); } else { timeout(stop, delay); }
        };
        if (document.readyState === 'complete') { run(); }
        else { listen(self, 'load', run, { once: true }); }
    }
    function multiup() {
        listen(document, 'click', event => {
            const button = event.target?.closest('button[link]');
            if (!button || button.parentElement?.localName !== 'form') { return; }
            let url;
            try { url = new URL(button.getAttribute('link'), location.href); } catch { return; }
            if (!['http:', 'https:'].includes(url.protocol)) { return; }
            event.preventDefault(); event.stopPropagation(); location.href = url.href;
        }, true);
    }
    // One observer and one frame batch for all selected DOM instructions; no polling.
    function dom() {
        if (!self.MutationObserver || !frame) { return; }
        const types = ['SET_ATTRIBUTE', 'REMOVE_ATTRIBUTE', 'REMOVE_CLASS', 'REMOVE_NODE_TEXT',
            'PREVENT_DIALOG', 'HREF_SANITIZER'];
        const entries = selected.filter(rule => types.includes(rule[0])).slice(0, 32)
            .map(([type, args]) => ({ type, args, done: false,
                name: type !== 'REMOVE_NODE_TEXT' ? null : args[0].startsWith('/')
                    ? matcher(args[0]) : name => name === args[0].toUpperCase(),
                text: type === 'REMOVE_NODE_TEXT' ? matcher(args[1]) : null,
            }));
        let pending;
        let stopped = false;
        const active = entry => !entry.done;
        function removeText(root, budget) {
            const visit = node => {
                if (--budget.value < 0) { return false; }
                for (const entry of entries) {
                    if (!active(entry) || entry.type !== 'REMOVE_NODE_TEXT') { continue; }
                    if (node !== document.currentScript && entry.name(node.nodeName) &&
                        node.textContent && entry.text(node.textContent)) { node.textContent = ''; }
                }
                return true;
            };
            if (!visit(root)) { return; }
            if (!document.createTreeWalker) { return; }
            const walker = document.createTreeWalker(root, 1);
            let node;
            while ((node = walker.nextNode()) && visit(node)) {}
        }
        function ready(entry) {
            const [type, args] = [entry.type, entry.args];
            if (type === 'SET_ATTRIBUTE') { return document.readyState === 'complete'; }
            if (type === 'REMOVE_ATTRIBUTE' || type === 'REMOVE_CLASS') {
                if (args[2].split(' ').includes('complete')) {
                    return document.readyState === 'complete';
                }
                if (type === 'REMOVE_CLASS' ||
                    args[2].split(' ').includes('asap')) { return true; }
                return document.readyState !== 'loading';
            }
            return type !== 'HREF_SANITIZER' || document.readyState !== 'loading';
        }
        function sweep() {
            pending = undefined;
            if (stopped) { return; }
            let budget = 4096;
            for (const entry of entries) {
                if (!active(entry) || !ready(entry)) { continue; }
                const { type, args } = entry;
                if (type === 'REMOVE_NODE_TEXT') { continue; }
                let selector;
                if (type === 'SET_ATTRIBUTE' || type === 'HREF_SANITIZER') { selector = args[0]; }
                else if (type === 'PREVENT_DIALOG') { selector = 'dialog' + args[0]; }
                else {
                    const escape = name => name.replace(/:/g, '\\:');
                    selector = args[1] || args[0].split('|').map(name =>
                        type === 'REMOVE_CLASS' ? '.' + escape(name) : '[' + escape(name) + ']')
                        .join(',');
                }
                let nodes;
                try { nodes = document.querySelectorAll(selector); } catch { entry.done = true; }
                if (!nodes) { continue; }
                const length = Math.min(nodes.length, 1024, budget);
                budget -= length;
                for (let i = 0; i < length; i++) {
                    const node = nodes[i];
                    try {
                        if (type === 'SET_ATTRIBUTE') {
                            let value = args[2].startsWith('[')
                                ? node.getAttribute(args[2].slice(1, -1)) || '' : args[2];
                            if (!args[2].startsWith('[') && /^\d+$/.test(value)) {
                                value = String(Number(value));
                            }
                            if (node.getAttribute(args[1]) !== value) {
                                node.setAttribute(args[1], value);
                            }
                        } else if (type === 'REMOVE_ATTRIBUTE') {
                            for (const name of args[0].split('|')) { node.removeAttribute(name); }
                        } else if (type === 'REMOVE_CLASS') {
                            const names = args[0].split('|')
                                .filter(name => node.classList.contains(name));
                            if (names.length) { node.classList.remove(...names); }
                        } else if (type === 'PREVENT_DIALOG') {
                            if (node.open && typeof node.close === 'function') { node.close(); }
                        } else if (type === 'HREF_SANITIZER') {
                            if (node.localName !== 'a' || !node.hasAttribute('href')) { continue; }
                            let text;
                            if (args[1].startsWith('[')) {
                                text = node.getAttribute(args[1].slice(1, -1));
                            } else if (args[1].startsWith('?')) {
                                text = new URL(node.href, location.href).searchParams
                                    .get(args[1].slice(1));
                            } else { text = node.textContent.trim(); }
                            if (!text || /[\x00-\x20\x7f]/.test(text)) { continue; }
                            const url = new URL(text, location.href);
                            if (['http:', 'https:'].includes(url.protocol) &&
                                node.href !== url.href) {
                                node.setAttribute('href', url.href);
                            }
                        }
                    } catch {}
                }
                if (type === 'REMOVE_ATTRIBUTE' && !args[2].split(' ').includes('stay')) {
                    entry.done = true;
                } else if (type === 'REMOVE_CLASS' && document.readyState === 'complete' &&
                    !args[2].split(' ').includes('stay')) { entry.done = true; }

                if (budget === 0) { break; }
            }
            if (!entries.some(active)) { observer.disconnect(); }
        }
        const schedule = () => {
            if (!stopped && pending === undefined) { pending = frame(sweep); }
        };
        const observer = new MutationObserver(mutations => {
            const budget = { value: 4096 };
            if (document.readyState === 'loading') {
                for (const mutation of mutations) {
                    for (const node of mutation.addedNodes || []) {
                        if (budget.value <= 0) { break; }
                        removeText(node, budget);
                    }
                    if (budget.value <= 0) { break; }
                }
            }
            schedule();
        });
        const attributeNames = new Set();
        const dialogs = entries.some(entry => entry.type === 'PREVENT_DIALOG');
        for (const entry of entries) {
            if (entry.type === 'REMOVE_CLASS') { attributeNames.add('class'); }
            if (entry.type === 'REMOVE_ATTRIBUTE') {
                for (const name of entry.args[0].split('|')) { attributeNames.add(name); }
            }
        }
        const observe = () => {
            const options = { childList: true, subtree: true };
            if (dialogs || attributeNames.size) {
                options.attributes = true;
                if (!dialogs) { options.attributeFilter = Array.from(attributeNames); }
            }
            observer.observe(document, options);
        };
        observe();
        removeText(document, { value: 4096 });
        sweep();
        listen(document, 'readystatechange', () => {
            if (document.readyState !== 'loading') {
                for (const entry of entries) {
                    if (entry.type === 'REMOVE_NODE_TEXT') { entry.done = true; }
                }
            }
            sweep();
        });
        listen(self, 'pagehide', () => {
            stopped = true; observer.disconnect();
            if (pending !== undefined && cancelFrame) { cancelFrame(pending); }
            pending = undefined;
        });
        listen(self, 'pageshow', event => {
            if (!event.persisted || !stopped) { return; }
            stopped = false; observe(); schedule();
        });
    }
    let timeoutInstalled = false;
    let evalInstalled = false;
    const timeoutOnce = () => {
        if (!timeoutInstalled) { timeoutInstalled = true; installTimeout(); }
    };
    const evalOnce = () => {
        if (!evalInstalled) { evalInstalled = true; evaluation(); }
    };
    const bab = () => { evalOnce(); timeoutOnce(); };
    const installers = new Map([
        ['PREVENT_SET_TIMEOUT', timeoutOnce], ['ADJUST_SET_TIMEOUT', timeoutOnce],
        ['PREVENT_SET_INTERVAL', installInterval], ['ADJUST_SET_INTERVAL', installInterval],
        ['PREVENT_ANIMATION_FRAME', animation], ['PREVENT_EVAL', evalOnce],
        ['PREVENT_BAB', bab], ['ABORT_STACK_TRACE', stackAbort],
        ['SET_LOCAL_STORAGE', args => storage('localStorage', args)],
        ['SET_SESSION_STORAGE', args => storage('sessionStorage', args)],
        ['SET_COOKIE', args => cookie(args, false)],
        ['SET_COOKIE_RELOAD', args => cookie(args, true)], ['REMOVE_COOKIE', removeCookie],
        ['CALL_NOTHROW', noThrow], ['PREVENT_CANVAS', canvas],
        ['PREVENT_ELEMENT_PROPERTY', freeze], ['SPOOF_CSS', spoof],
        ['PREVENT_WEBRTC', rtc], ['WEBRTC_IF', rtc], ['DISABLE_NEWTAB_LINKS', newtabs],
        ['PREVENT_NAVIGATION', navigation], ['PREVENT_REFRESH', refresh], ['MULTIUP', multiup],
        ['CLEAR_WINDOW_NAME', () => { if (self === self.top) { self.name = ''; } }],
        ['ALERT_BUSTER', () => wrap(self, 'alert', () => undefined)],
        ['RESOURCE', args => {
            const factory = resources[args[0]];
            if (typeof factory === 'function') { factory(timeout, clearTimeout); }
        }],
    ]);
    for (const type of ['SET_ATTRIBUTE', 'REMOVE_ATTRIBUTE', 'REMOVE_CLASS', 'REMOVE_NODE_TEXT',
        'PREVENT_DIALOG', 'HREF_SANITIZER']) { installers.set(type, dom); }
    return installers;
})
