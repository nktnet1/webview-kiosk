// Native-like fixture; the optional browser suite also exercises actual Chromium.
export function xhrFixture() {
    const values = new WeakMap();
    const sends = [];
    class XHR extends EventTarget {
        constructor() {
            super(); values.set(this, { state: 0, type: '', status: 0, text: '', value: '' });
        }
        dispatchEvent(event) {
            const result = super.dispatchEvent(event);
            this['on' + event.type]?.(event);
            return result;
        }
        get readyState() { return values.get(this).state; }
        get status() { return values.get(this).status; }
        get statusText() { return this.status === 200 ? 'OK' : ''; }
        get responseURL() { return values.get(this).url || ''; }
        get responseType() { return values.get(this).type; }
        set responseType(type) {
            const value = values.get(this);
            if (value.state >= 3) { throw new DOMException('State', 'InvalidStateError'); }
            value.type = type;
        }
        get response() { return values.get(this).value; }
        get responseText() {
            if (!['', 'text'].includes(this.responseType)) {
                throw new DOMException('Type', 'InvalidStateError');
            }
            return values.get(this).text;
        }
        get responseXML() {
            if (!['', 'document'].includes(this.responseType)) {
                throw new DOMException('Type', 'InvalidStateError');
            }
            return values.get(this).xml || null;
        }
        open(method, url, async = true) {
            if (!method || url === 'invalid') { throw new DOMException('Syntax', 'SyntaxError'); }
            Object.assign(values.get(this), { state: 1, status: 0, text: '', value: '',
                type: '', method, url, async, sent: false });
            this.dispatchEvent(new Event('readystatechange'));
        }
        send(body) {
            const value = values.get(this);
            if (value.state !== 1 || value.sent) {
                throw new DOMException('State', 'InvalidStateError');
            }
            value.sent = true; sends.push([this, body]);
        }
        abort() { Object.assign(values.get(this), { state: 0, status: 0, sent: false }); }
        setRequestHeader() {
            const value = values.get(this);
            if (value.state !== 1 || value.sent) {
                throw new DOMException('State', 'InvalidStateError');
            }
        }
        getResponseHeader() { return this.readyState >= 2 ? 'native header' : null; }
        getAllResponseHeaders() { return this.readyState >= 2 ? 'x-native: yes\r\n' : ''; }
        complete(text, { state = 4, json = false } = {}) {
            Object.assign(values.get(this), { state, status: 200, text,
                value: json ? JSON.parse(text) : text });
            this.dispatchEvent(new Event('readystatechange'));
        }
    }
    return { XHR, sends, globals: { XMLHttpRequest: XHR, Event, Blob,
        TextDecoder, DOMException, ArrayBuffer } };
}
