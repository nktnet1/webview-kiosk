import assert from 'node:assert/strict';
import test from 'node:test';
import { page } from './scriptlet-fixture.mjs';

test('trusted argument replacements keep receivers, conditions and missing arguments', () => {
    const p = page([
        ['TRUSTED_REPLACE_ARGUMENT', ['method', '0', 'json:"safe"', 'condition', 'advert']],
    ], { before: 'method = function(value) { return [this.label, value, arguments.length]; };' });
    p.install();
    assert.equal(p.run('JSON.stringify(method.call({label:"receiver"}, "advert"))'),
        '["receiver","safe",1]');
    assert.equal(p.run('method("normal")[1]'), 'normal');
    assert.equal(p.run('method()[2]'), 0);
});

test('argument edits support receivers, offsets, addition and regex', () => {
    const p = page([
        ['TRUSTED_REPLACE_ARGUMENT', ['String.prototype.includes', 'this',
            'repl:/advert/content/g', 'condition', 'advert']],
        ['TRUSTED_REPLACE_ARGUMENT', ['method', '-1', 'add:2']],
    ], { before: 'method = (...args) => args;' });
    p.install();
    assert.equal(p.run('"advert advert".includes("content content")'), true);
    assert.equal(p.run('JSON.stringify(method(1, 2))'), '[1,4]');
    assert.equal(p.run('method().length'), 0);
});

test('outbound editing keeps nonstrings, native errors, conditions and base64 boundaries', () => {
    const p = page([
        ['TRUSTED_REPLACE_OUTBOUND_TEXT', ['method', 'advert', 'content', 'condition', 'token']],
        ['TRUSTED_REPLACE_OUTBOUND_TEXT', ['encoded', 'advert', 'content', 'encoding', 'base64']],
    ], { globals: { btoa }, before:
        'method = value => { if (value === "throw") throw new TypeError("native"); ' +
        'return value; }; encoded = value => value;' });
    p.install();
    assert.equal(p.run('method("token advert")'), 'token content');
    assert.equal(p.run('method("advert")'), 'advert');
    assert.equal(p.run('method(12)'), 12);
    assert.equal(p.run('atob(encoded(btoa("advert")))'), 'content');
    assert.equal(p.run('encoded("invalid base64!")'), 'invalid base64!');
    assert.throws(() => p.run('method("throw")'), /native/);
});

test('replacement expansion preserves captures and fails open before oversized output allocation',
    () => {
        const p = page([
            ['TRUSTED_REPLACE_OUTBOUND_TEXT', ['method', '/(advert)/', '$1 $$ $&']],
            ['TRUSTED_REPLACE_OUTBOUND_TEXT', ['large', '/a/g', 'x'.repeat(8192)]],
        ], { before: 'method = value => value; large = () => "a".repeat(65536);' });
        p.install();
        assert.equal(p.run('method("advert")'), 'advert $ advert');
        assert.equal(p.run('large()'), 'a'.repeat(65536));
    });

test('suppression matches every signature position and aborts only matching calls', () => {
    const p = page([
        ['TRUSTED_SUPPRESS_NATIVE_METHOD', ['method', '"advert" | true', '', '']],
        ['TRUSTED_SUPPRESS_NATIVE_METHOD', ['Function.prototype.constructor',
            '"debugger"', 'abort', '']],
    ], { before: 'method = (...args) => args.length;' });
    p.install();
    assert.equal(p.run('method("advert", true)'), undefined);
    assert.equal(p.run('method("advert", false)'), 2);
    assert.equal(p.run('method("content", true)'), 2);
    assert.throws(() => p.run('(function() {}).constructor("debugger")'),
        { name: 'ReferenceError' });
    assert.equal(p.run('(function() {}).constructor("return 7")()'), 7);
});

test('clipboard protection preserves promises, exclusions and native failures', async () => {
    const writes = [];
    const p = page([['PREVENT_CLIPBOARD_WRITE', ['danger', 'excludeMatches', 'allowed']]],
        { globals: { navigator: { clipboard: { writeText(text) {
            writes.push(text);
            return text === 'denied' ? Promise.reject(new Error('native denied'))
                : Promise.resolve('native success');
        } } } } });
    p.install();
    assert.equal(await p.run('navigator.clipboard.writeText("danger")'), undefined);
    assert.equal(await p.run('navigator.clipboard.writeText("danger allowed")'), 'native success');
    await assert.rejects(p.run('navigator.clipboard.writeText("denied")'), /native denied/);
    assert.deepEqual(writes, ['danger allowed', 'denied']);
});

test('element method overrides retain selectors and native calls', () => {
    class Element {
        constructor(match) { this.match = match; }
        matches() { return this.match; }
        click() { return 'native'; }
    }
    const p = page([['TRUSTED_OVERRIDE_ELEMENT_METHOD',
        ['Element.prototype.click', '.advert', 'json:"blocked"']]], { globals: { Element } });
    p.install();
    assert.equal(p.run('new Element(true).click()'), 'blocked');
    assert.equal(p.run('new Element(false).click()'), 'native');
});
