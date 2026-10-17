/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Adapted from uBlock Origin, Copyright (C) 2019-2026 Raymond Hill and contributors.
 * Modified for Webview Kiosk on 2026-10-16: bounded response bodies and XHR lifecycle.
 * Source attribution and licence: META-INF/ublock-NOTICE.txt.
 */
(function(env) {
    'use strict';
    const { selected, wrap, apply, define, descriptor, matcher, nativeThen,
        NativeRequest, NativeResponse, jsonPruner } = env;
    const parse = JSON.parse;
    const stringify = JSON.stringify;
    const NativeURL = self.URL;
    const NativeHeaders = self.Headers;
    const NativeParser = self.DOMParser;
    const NativeSerializer = self.XMLSerializer;
    const NativeBlob = self.Blob;
    const NativeBuffer = self.ArrayBuffer;
    const NativeXHR = self.XMLHttpRequest;
    const NativeEvent = self.Event;
    const NativeProgress = self.ProgressEvent;
    const NativeException = self.DOMException;
    const NativeError = self.Error;
    const NativePromise = self.Promise;
    const race = NativePromise.race.bind(NativePromise);
    const timeout = self.setTimeout.bind(self);
    const clear = self.clearTimeout.bind(self);
    const MAX_BODY = 1024 * 1024;
    const MAX_RULES = 32;
    const requestFields = raw => raw && raw !== '*' ? raw.split(' ').map(part => {
        const colon = part.indexOf(':');
        const name = part.slice(0, colon);
        return colon > 0 && ['url', 'method'].includes(name)
            ? [name, matcher(part.slice(colon + 1), true)] : ['url', matcher(part, true)];
    }) : [];
    const matches = (fields, details) =>
        fields.every(([name, match]) => match(details[name]));
    const textBody = directive => directive === 'emptyObj' ? '{}'
        : directive === 'emptyArr' ? '[]' : directive === 'emptyStr' ? '' : directive;
    const invalid = () => new NativeException('Invalid XMLHttpRequest state', 'InvalidStateError');

    function xml(text, args) {
        if (!NativeParser || !NativeSerializer || !/^\s*</.test(text) ||
            /<!DOCTYPE|<!ENTITY/i.test(text)) { return text; }
        const doc = new NativeParser().parseFromString(text, 'text/xml');
        if (doc.getElementsByTagName('parsererror').length) { return text; }
        if (doc.getElementsByTagName('*').length > 4096) { return text; }
        if (args[1] && !doc.querySelector(args[1])) { return text; }
        const nodes = doc.querySelectorAll(args[0]);
        if (!nodes.length || nodes.length > 1024 ||
            Array.from(nodes).includes(doc.documentElement)) { return text; }
        for (const node of nodes) { node.remove(); }
        return new NativeSerializer().serializeToString(doc);
    }
    function playlist(text, match) {
        if (!/^\s*#EXTM3U(?:\s|$)/.test(text)) { return text; }
        const lines = text.split(/\r\n|\n|\r/);
        if (lines.length > 16384) { return text; }
        const removed = new Set();
        for (let i = 0; i + 1 < lines.length; i++) {
            if (!lines[i].startsWith('#EXTINF:') || !lines[i + 1] ||
                lines[i + 1].startsWith('#') || !match(lines[i + 1])) { continue; }
            removed.add(i); removed.add(++i);
            if (lines[i + 1]?.startsWith('#EXT-X-DISCONTINUITY')) { removed.add(i + 1); }
        }
        return removed.size ? lines.filter((line, i) => !removed.has(i)).join('\n') : text;
    }
    function dash(text, selector) {
        if (!NativeParser || !NativeSerializer || /<!DOCTYPE|<!ENTITY/i.test(text)) { return text; }
        const doc = new NativeParser().parseFromString(text, 'text/xml');
        if (doc.querySelector('parsererror') || doc.documentElement?.localName !== 'MPD' ||
            doc.querySelectorAll('*').length > 4096) { return text; }
        const selected = Array.from(doc.querySelectorAll(selector));
        const periods = Array.from(doc.documentElement.children)
            .filter(node => node.localName === 'Period');
        if (!selected.length || selected.length > 128 || periods.length > 128 ||
            selected.some(node => !periods.includes(node))) { return text; }
        const seconds = raw => {
            const match = /^PT(?:(\d+)D)?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$/.exec(raw);
            if (!match || !match.slice(1).some(Boolean)) { return null; }
            const value = Number(match[1] || 0) * 86400 + Number(match[2] || 0) * 3600 +
                Number(match[3] || 0) * 60 + Number(match[4] || 0);
            return Number.isFinite(value) && value <= 864000 ? value : null;
        };
        const durations = periods.map(period => seconds(period.getAttribute('duration') || ''));
        if (durations.some(value => value === null) || periods.some(period =>
            seconds(period.getAttribute('start') || '') === null)) { return text; }
        let cursor = 0;
        periods.forEach((period, index) => {
            const before = period.getAttribute('start');
            const after = 'PT' + cursor + 'S';
            period.setAttribute('start', after);
            if (period.hasAttribute('id')) {
                period.setAttribute('id', period.getAttribute('id').replace(before, after));
            }
            if (selected.includes(period)) { period.setAttribute('duration', 'PT0S'); }
            else { cursor += durations[index]; }
        });
        if (doc.documentElement.hasAttribute('mediaPresentationDuration')) {
            doc.documentElement.setAttribute('mediaPresentationDuration', 'PT' + cursor + 'S');
        }
        return new NativeSerializer().serializeToString(doc);
    }
    const responseTypes = new Set(['JSON_PRUNE_FETCH', 'JSON_PRUNE_XHR', 'XML_PRUNE',
        'MPEGDASH_PRUNE',
        'M3U_PRUNE', 'TRUSTED_REPLACE_FETCH', 'TRUSTED_REPLACE_XHR']);
    const edits = selected.filter(rule => responseTypes.has(rule[0])).slice(0, MAX_RULES)
        .map(([type, args]) => {
            const json = type.startsWith('JSON_PRUNE_');
            const replace = type.startsWith('TRUSTED_REPLACE_');
            const mpd = type === 'MPEGDASH_PRUNE';
            const fields = requestFields(json ? args[3] : replace ? args[2] : mpd ? args[1] : '');
            const url = matcher(json || replace || mpd ? '' : args[type === 'XML_PRUNE' ? 2 : 1]);
            const prune = json ? jsonPruner([args]) : null;
            const stack = json && args[5] ? matcher(args[5], true) : null;
            const end = args[0].lastIndexOf('/');
            const pattern = replace && args[0].startsWith('/') && end > 0 &&
                /^[dgimsuvy]*$/.test(args[0].slice(end + 1))
                ? new RegExp(args[0].slice(1, end), args[0].slice(end + 1)) : args[0];
            const segment = type === 'M3U_PRUNE' ? matcher(args[0]) : null;
            return { type, args, fields, url, stack,
                transform(text) {
                    if (json) {
                        const value = apply(parse, JSON, [text]);
                        const before = apply(stringify, JSON, [value]);
                        const after = apply(stringify, JSON, [prune(value)]);
                        return before === after ? text : after;
                    }
                    if (replace) { return text.replace(pattern, args[1]); }
                    if (type === 'XML_PRUNE') { return xml(text, args); }
                    if (mpd) { return dash(text, args[0]); }
                    return playlist(text, segment);
                } };
        });
    const applicable = (kind, details, stack) => edits.filter(edit =>
        (edit.type !== 'MPEGDASH_PRUNE' || kind === 'xhr') &&
        (!edit.type.endsWith('_FETCH') || kind === 'fetch') &&
        (!edit.type.endsWith('_XHR') || kind === 'xhr') &&
        matches(edit.fields, details) && edit.url(details.url) &&
        (!edit.stack || edit.stack(stack)));
    function transform(text, entries) {
        if (typeof text !== 'string' || text.length > MAX_BODY) { return text; }
        // Each instruction either commits its bounded edits or retains its input.
        let remaining = 4 * MAX_BODY;
        for (const edit of entries) {
            remaining -= text.length;
            if (remaining < 0) { break; }
            try {
                const value = edit.transform(text);
                if (typeof value === 'string' && value.length <= MAX_BODY) { text = value; }
            } catch {}
        }
        return text;
    }
    function fetchDetails(args) {
        try {
            const input = args[0];
            const request = NativeRequest && input instanceof NativeRequest;
            const url = request ? input.url : typeof input === 'string' ? input
                : NativeURL && input instanceof NativeURL ? input.href : null;
            const method = args[1]?.method ?? (request ? input.method : 'GET');
            return typeof url === 'string' && typeof method === 'string'
                ? { url, method } : null;
        } catch { return null; }
    }
    async function readBounded(response) {
        if (!response.body || typeof response.body.getReader !== 'function' ||
            !self.TextDecoder || Number(response.headers.get('content-length')) > MAX_BODY) {
            return null;
        }
        const clone = response.clone();
        const reader = clone.body.getReader();
        const decoder = new self.TextDecoder('utf-8', { fatal: true });
        let text = '';
        let bytes = 0;
        let timer;
        const read = async () => {
            for (;;) {
                const { done, value } = await reader.read();
                if (done) { return text + decoder.decode(); }
                bytes += value.byteLength;
                if (bytes > MAX_BODY) { return null; }
                text += decoder.decode(value, { stream: true });
            }
        };
        try {
            return await race([read(), new NativePromise(resolve => {
                timer = timeout(() => resolve(null), 2000);
            })]);
        } finally {
            clear(timer);
            // Cancellation of a tee branch can wait for the original branch.
            // Keep that promise out of the caller's response path.
            reader.cancel().catch(() => {});
        }
    }
    function metadata(response, original) {
        for (const name of ['url', 'type', 'redirected']) {
            define(response, name, { value: original[name], configurable: true });
        }
        const clone = response.clone;
        define(response, 'clone', { configurable: true, value: function() {
            return metadata(apply(clone, this, []), original);
        } });
        return response;
    }
    let fetchInstalled = false;
    function fetchResponses() {
        if (fetchInstalled || !NativeResponse || !NativeHeaders) { return; }
        fetchInstalled = true;
        wrap(self, 'fetch', (target, receiver, args) => {
            const details = fetchDetails(args);
            const entries = details
                ? applicable('fetch', details, new NativeError().stack || '') : [];
            const result = apply(target, receiver, args);
            if (!entries.length) { return result; }
            return apply(nativeThen, result, [async original => {
                try {
                    if (original.status < 200 || original.status > 599 ||
                        original.status === 204 || original.status === 205 ||
                        original.status === 304 || original.bodyUsed ||
                        ['opaque', 'opaqueredirect', 'error'].includes(original.type)) {
                        return original;
                    }
                    const encoding = original.headers.get('content-type') || '';
                    if (/charset\s*=/i.test(encoding) &&
                        !/charset\s*=\s*["']?utf-8\b/i.test(encoding)) { return original; }
                    const text = await readBounded(original);
                    if (text === null) { return original; }
                    const after = transform(text, entries);
                    if (after === text) { return original; }
                    const headers = new NativeHeaders(original.headers);
                    headers.delete('content-length'); headers.delete('content-encoding');
                    headers.delete('etag'); headers.delete('content-md5');
                    return metadata(new NativeResponse(after, {
                        status: original.status, statusText: original.statusText, headers,
                    }), original);
                } catch { return original; }
            }]);
        });
    }

    const shadows = ['readyState', 'status', 'statusText', 'responseURL', 'response',
        'responseText', 'responseXML', 'responseType', 'withCredentials'];
    let xhrInstalled = false;
    function xhrResponses() {
        if (xhrInstalled || !NativeXHR || !NativeEvent || !NativeException) { return; }
        xhrInstalled = true;
        const proto = NativeXHR.prototype;
        const states = new WeakMap();
        const preventers = selected.filter(rule =>
            ['PREVENT_XHR', 'TRUSTED_PREVENT_XHR'].includes(rule[0])).slice(0, MAX_RULES)
            .map(([type, args]) => ({ fields: requestFields(args[0]),
                body: textBody(args[1]), trusted: type === 'TRUSTED_PREVENT_XHR' }));
        function nativeDescriptor(name) {
            for (let owner = proto; owner; owner = Object.getPrototypeOf(owner)) {
                const desc = descriptor(owner, name);
                if (desc) { return desc; }
            }
        }
        const responseType = nativeDescriptor('responseType');
        const credentials = nativeDescriptor('withCredentials');
        function emit(xhr, type, loaded = 0) {
            const event = NativeProgress && type !== 'readystatechange'
                ? new NativeProgress(type, { lengthComputable: true, loaded, total: loaded })
                : new NativeEvent(type);
            xhr.dispatchEvent(event);
        }
        function clearState(xhr) {
            const old = states.get(xhr);
            if (old?.task !== undefined) { clear(old.task); }
            if (old?.synthetic) { for (const name of shadows) { delete xhr[name]; } }
            states.delete(xhr);
        }
        wrap(proto, 'open', (target, xhr, args) => {
            clearState(xhr);
            const details = typeof args[0] === 'string' && typeof args[1] === 'string'
                ? { method: args[0], url: args[1] } : null;
            const state = { phase: 1, sent: false, synthetic: false, cache: new Map(),
                sync: args[2] === false, entries: details
                    ? applicable('xhr', details, new NativeError().stack || '') : [],
                prevent: details ? preventers.find(test => matches(test.fields, details)) : null,
                url: details?.url || '', task: undefined };
            states.set(xhr, state);
            try { return apply(target, xhr, args); }
            catch (error) {
                if (states.get(xhr) === state) { states.delete(xhr); }
                throw error;
            }
        });
        function prepare(xhr, state) {
            const type = xhr.responseType;
            const body = state.prevent.body;
            state.text = body;
            state.xml = null;
            state.mime = 'text/plain';
            if (type === 'json') {
                state.value = {}; state.text = '{}'; state.mime = 'application/json';
            } else if (type === 'arraybuffer') {
                state.value = new NativeBuffer(0); state.text = '';
                state.mime = 'application/octet-stream';
            } else if (type === 'blob' && NativeBlob) {
                state.value = new NativeBlob([]); state.text = '';
                state.mime = 'application/octet-stream';
            } else if (type === 'document' && NativeParser) {
                state.value = state.xml = new NativeParser().parseFromString('', 'text/html');
                state.text = ''; state.mime = 'text/html';
            } else { state.value = body; }
            state.length = NativeBlob ? new NativeBlob([state.text]).size : state.text.length;
        }
        function shadow(xhr, state) {
            // Leave frozen or page-owned response properties on their native path.
            if (shadows.some(name => descriptor(xhr, name)) || !responseType?.get ||
                !responseType?.set || !Object.isExtensible(xhr)) { return false; }
            state.synthetic = true;
            state.phase = 1; state.failed = false;
            state.text = ''; state.value = ''; state.xml = null;
            const getter = (name, get, set) => define(xhr, name,
                { configurable: true, enumerable: true, get, ...(set ? { set } : {}) });
            getter('readyState', () => state.phase);
            getter('status', () => state.phase >= 2 && !state.failed ? 200 : 0);
            getter('statusText', () => xhr.status ? 'OK' : '');
            getter('responseURL', () => state.phase >= 2 && !state.failed
                ? new NativeURL(state.url, location.href).href.replace(/#.*$/, '') : '');
            getter('responseType', () => apply(responseType.get, xhr, []), value => {
                if (state.phase >= 3) { throw invalid(); }
                apply(responseType.set, xhr, [value]);
            });
            if (credentials?.get && credentials?.set) {
                getter('withCredentials', () => apply(credentials.get, xhr, []), value => {
                    if (state.sent || state.phase > 1) { throw invalid(); }
                    apply(credentials.set, xhr, [value]);
                });
            }
            getter('response', () => {
                if (state.failed) { return ['', 'text'].includes(xhr.responseType) ? '' : null; }
                if (state.phase < (['', 'text'].includes(xhr.responseType) ? 3 : 4)) {
                    return ['', 'text'].includes(xhr.responseType) ? '' : null;
                }
                return state.value;
            });
            getter('responseText', () => {
                if (!['', 'text'].includes(xhr.responseType)) { throw invalid(); }
                return state.phase >= 3 && !state.failed ? state.text : '';
            });
            getter('responseXML', () => {
                if (!['', 'document'].includes(xhr.responseType)) { throw invalid(); }
                return state.phase === 4 && !state.failed ? state.xml : null;
            });
            return true;
        }
        wrap(proto, 'send', (target, xhr, args) => {
            const state = states.get(xhr);
            if (!state?.prevent) { return apply(target, xhr, args); }
            if (state.sent || state.synthetic && state.phase !== 1) { throw invalid(); }
            if (xhr.responseType === 'document' && !NativeParser ||
                xhr.responseType === 'blob' && !NativeBlob ||
                xhr.responseType === 'arraybuffer' && !NativeBuffer) {
                return apply(target, xhr, args);
            }
            if (!shadow(xhr, state)) { return apply(target, xhr, args); }
            state.sent = true;
            prepare(xhr, state);
            const current = () => states.get(xhr) === state && state.sent && !state.failed;
            const finish = () => {
                state.task = undefined;
                for (const phase of state.sync ? [4] : [2, 3, 4]) {
                    if (!current()) { return; }
                    if (phase === 3) { prepare(xhr, state); }
                    state.phase = phase;
                    emit(xhr, 'readystatechange');
                    if (phase === 3 && current()) { emit(xhr, 'progress', state.length); }
                }
                if (!current()) { return; }
                emit(xhr, 'load', state.length);
                if (current()) { emit(xhr, 'loadend', state.length); }
            };
            if (state.sync) { finish(); }
            else {
                emit(xhr, 'loadstart');
                if (current()) { state.task = timeout(finish, 0); }
            }
        });
        wrap(proto, 'abort', (target, xhr, args) => {
            const state = states.get(xhr);
            if (!state?.synthetic) { return apply(target, xhr, args); }
            if (state.task !== undefined) { clear(state.task); state.task = undefined; }
            apply(target, xhr, args);
            const inFlight = state.sent && state.phase !== 4;
            state.failed = true; state.sent = false;
            if (inFlight) {
                state.phase = 4;
                for (const type of ['readystatechange', 'abort', 'loadend']) {
                    if (states.get(xhr) !== state) { return; }
                    emit(xhr, type);
                }
            }
            if (states.get(xhr) === state) { state.phase = 0; }
        });
        wrap(proto, 'setRequestHeader', (target, xhr, args) => {
            const state = states.get(xhr);
            if (state?.synthetic && (state.sent || state.phase !== 1)) { throw invalid(); }
            return apply(target, xhr, args);
        });
        wrap(proto, 'overrideMimeType', (target, xhr, args) => {
            const state = states.get(xhr);
            if (state?.synthetic && state.phase >= 3) { throw invalid(); }
            return apply(target, xhr, args);
        });
        const headers = xhr => {
            const state = states.get(xhr);
            if (!state?.synthetic) { return null; }
            return state.phase < 2 || state.failed ? {} : {
                'content-type': state.mime || 'text/plain',
                'content-length': String(state.length || 0),
            };
        };
        wrap(proto, 'getResponseHeader', (target, xhr, args) => {
            const values = headers(xhr);
            if (values === null || typeof args[0] !== 'string') {
                return apply(target, xhr, args);
            }
            return values[args[0].toLowerCase()] ?? null;
        });
        wrap(proto, 'getAllResponseHeaders', (target, xhr, args) => {
            const values = headers(xhr);
            return values === null ? apply(target, xhr, args) : Object.entries(values)
                .map(([key, value]) => key + ': ' + value + '\r\n').join('');
        });
        for (const name of ['response', 'responseText', 'responseXML']) {
            const desc = nativeDescriptor(name);
            if (!desc?.get || desc.configurable === false) { continue; }
            define(proto, name, { ...desc, get: function() {
                const value = apply(desc.get, this, []);
                const state = states.get(this);
                if (!state?.entries.length || this.readyState !== 4 || state.synthetic) {
                    return value;
                }
                if (state.cache.has(value)) { return state.cache.get(value); }
                let result = value;
                if (typeof value === 'string') { result = transform(value, state.entries); }
                else if (value && this.responseType === 'json') {
                    try {
                        const text = apply(stringify, JSON, [value]);
                        const after = transform(text, state.entries.filter(edit =>
                            edit.type === 'JSON_PRUNE_XHR'));
                        if (after !== text) { result = apply(parse, JSON, [after]); }
                    } catch {}
                } else if (value?.nodeType === 9 && NativeSerializer && NativeParser) {
                    try {
                        const text = new NativeSerializer().serializeToString(value);
                        const after = transform(text, state.entries.filter(edit =>
                            ['XML_PRUNE', 'MPEGDASH_PRUNE'].includes(edit.type)));
                        if (after !== text) {
                            result = new NativeParser().parseFromString(after, 'text/xml');
                        }
                    } catch {}
                }
                state.cache.set(value, result);
                return result;
            } });
        }
    }
    const both = () => { fetchResponses(); xhrResponses(); };
    return new Map([
        ['PREVENT_XHR', xhrResponses], ['TRUSTED_PREVENT_XHR', xhrResponses],
        ['JSON_PRUNE_XHR', xhrResponses], ['TRUSTED_REPLACE_XHR', xhrResponses],
        ['JSON_PRUNE_FETCH', fetchResponses], ['TRUSTED_REPLACE_FETCH', fetchResponses],
        ['XML_PRUNE', both], ['M3U_PRUNE', both],
        ['MPEGDASH_PRUNE', xhrResponses],
    ]);
})
