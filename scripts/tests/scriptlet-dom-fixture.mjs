// Controlled DOM lifecycle fixture. This tests hooks, not Chromium layout or parsing.
export function domFixture() {
    const nodes = [];
    const observers = [];
    class Node {
        constructor() { this._text = ''; this.nodeName = '#document'; this.children = []; }
        get textContent() { return this._text; }
        set textContent(value) { this._text = String(value); }
    }
    class Element extends Node {
        constructor(name = 'div', attrs = {}) {
            super(); this.localName = name; this.nodeName = name.toUpperCase();
            this.nodeType = 1; this.attrs = new Map(Object.entries(attrs)); this._html = '';
            this.classes = new Set((attrs.class || '').split(' ').filter(Boolean));
            this.classList = {
                remove: (...names) => names.forEach(name => this.classes.delete(name)),
                contains: name => this.classes.has(name) };
            this.open = false; this.closed = 0;
        }
        getAttribute(name) { return this.attrs.get(name) ?? null; }
        hasAttribute(name) { return this.attrs.has(name); }
        setAttribute(name, value) { this.attrs.set(name, String(value)); }
        removeAttribute(name) { this.attrs.delete(name); }
        get innerHTML() { return this._html; }
        set innerHTML(value) { this._html = String(value); }
        get outerHTML() { return this._html; }
        set outerHTML(value) { this._html = String(value); }
        get href() {
            return new URL(this.getAttribute('href') || '', 'https://site.example/').href;
        }
        set href(value) { this.setAttribute('href', value); }
        matches(selector) {
            if (selector === '*') { return true; }
            if (selector.startsWith('.')) { return this.classes.has(selector.slice(1)); }
            if (selector.startsWith('#')) { return this.getAttribute('id') === selector.slice(1); }
            const prefix = selector.split('[')[0];
            const [tag, className] = prefix.split('.');
            if (className && !this.classes.has(className)) { return false; }
            if (tag && tag !== this.localName) { return false; }
            const attr = /\[([^=\]]+)(?:="([^"]*)"(?: i)?)?\]/.exec(selector);
            return !attr || (this.hasAttribute(attr[1]) &&
                (attr[2] === undefined || this.getAttribute(attr[1]).toLowerCase() === attr[2]));
        }
        closest(selector) {
            let node = this;
            while (node) { if (node.matches(selector)) { return node; } node = node.parentElement; }
            return null;
        }
        getBoundingClientRect() { return new Rect(1, 2, 10, 20); }
        close() { this.open = false; this.closed++; }
    }
    class Script extends Element {
        constructor(text = '', src = '') {
            super('script'); this.textContent = text; this.src = src;
        }
        get text() { return this.textContent; }
        set text(value) { this.textContent = value; }
        get src() { return this.getAttribute('src') || ''; }
        set src(value) { this.setAttribute('src', value); }
    }
    class Rect {
        constructor(x, y, width, height) { Object.assign(this, { x, y, width, height }); }
    }
    class Observer {
        constructor(callback) {
            this.callback = callback; this.active = false; observers.push(this);
        }
        observe() { this.active = true; }
        disconnect() { this.active = false; }
    }
    const document = Object.assign(new Node(), {
        readyState: 'loading', currentScript: null,
        querySelectorAll(selector) { return nodes.filter(node => node.matches(selector)); },
        createTreeWalker(root) {
            let index = 0;
            const children = root === document ? nodes : root.children;
            return { nextNode: () => children[index++] || null };
        },
    });
    return {
        document, Element, Script, nodes, observers,
        globals: { document, Node, Element, HTMLElement: Element, HTMLScriptElement: Script,
            DOMRect: Rect, MutationObserver: Observer, getComputedStyle: () => ({
                display: 'block', width: '10px',
                getPropertyValue(name) { return this[name] || ''; },
            }) },
        add(node) { nodes.push(node); return node; },
        mutate(addedNodes = [], type = 'childList') {
            for (const observer of observers) {
                if (observer.active) { observer.callback([{ type, addedNodes }]); }
            }
        },
        ready(p, state) {
            document.readyState = state;
            p.emit('readystatechange', {}, document);
        },
    };
}
