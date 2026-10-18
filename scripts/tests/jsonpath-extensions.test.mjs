import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';
import { page } from './scriptlet-fixture.mjs';

const step = (name, filter = null) => [false, name === null ? [] : [name], filter];
const predicate = (path, op = '', value = null, negated = false) =>
    [negated, path.map(name => step(name)), op, value, false];
const args = (steps, mutation = null, extended = true) =>
    ['', JSON.stringify([true, steps, [], mutation, extended]), '', '', ''];

test('reverse slices and overlapping selectors delete original array indices once', () => {
    const selectors = [{ slice: [5, 1, -2] }, 3, 0];
    const p = page([['JSON_EDIT', args([step('items'), [false, selectors, null]])]]);
    p.install();
    assert.equal(p.run('JSON.stringify(JSON.parse(\'{"items":[0,1,2,3,4,5,6]}\'))'),
        '{"items":[1,2,4,6]}');
});

test('logical filters distinguish existing false values from absent values', () => {
    const filter = ['||', predicate(['flag']), predicate(['other'])];
    const p = page([['JSON_EDIT', args([step(null, filter)])]]); p.install();
    assert.equal(p.run('JSON.stringify(JSON.parse(\'[{"flag":false},{"flag":null},{}]\'))'),
        '[{}]');
    const negative = page([['JSON_EDIT', args([step(null,
        ['!', ['||', predicate(['flag']), predicate(['other'])]])])]]);
    negative.install();
    assert.equal(negative.run('JSON.stringify(JSON.parse(\'[{"flag":false},{}]\'))'),
        '[{"flag":false}]');
});

test('comparisons and recursive traversal never coerce objects or follow prototype names', () => {
    const p = page([['JSON_EDIT', args([step(null, ['||',
        predicate(['value'], '>', 1), predicate(['value'], '^=', 'ad')])])]]);
    p.install();
    assert.equal(p.run(`(() => {
        let reads = 0;
        const bad = { get toString() { reads++; return () => 'ad'; },
            get valueOf() { reads++; return () => 2; } };
        const input = [{ value: bad }, { get value() { reads++; return 2; } }];
        const result = JSON.parse('{}', () => input);
        return result.length === 2 && reads === 0;
    })()`), true);
    const q = page([['JSON_EDIT', args([[true, ['ads'], null]])]]); q.install();
    assert.equal(q.run(`(() => {
        let reads = 0;
        const input = { constructor: { ads: true }, ordinary: { ads: true } };
        Object.defineProperty(input, 'accessor', { enumerable: true,
            get() { reads++; return { ads: true }; } });
        JSON.parse('{}', () => input);
        return input.constructor.ads && !Object.hasOwn(input.ordinary, 'ads') && reads === 0;
    })()`), true);
});

test('array compaction avoids species, inherited getters and accessor-backed shifts', () => {
    const p = page([['JSON_EDIT', args([step(0)])]]); p.install();
    assert.equal(p.run(`(() => {
        let reads = 0;
        const array = [0, , 2];
        Object.defineProperty(array, 'constructor', { get() { reads++; return Array; } });
        const proto = Object.create(Array.prototype);
        Object.defineProperty(proto, '1', { get() { reads++; return 1; } });
        Object.setPrototypeOf(array, proto);
        JSON.parse('{}', () => array);
        return reads === 0 && array.length === 2 &&
            !Object.hasOwn(array, '0') && Object.getOwnPropertyDescriptor(array, '1').value === 2;
    })()`), true);
    assert.equal(p.run(`(() => {
        let reads = 0;
        const array = [0, 1, 2];
        Object.defineProperty(array, '2', { configurable: true,
            get() { reads++; return 2; } });
        JSON.parse('{}', () => array);
        return reads === 0 && array.length === 3 && array[0] === 0;
    })()`), true);
    assert.equal(p.run(`(() => {
        const array = [0, 1, 2];
        Object.defineProperty(array, '2', { enumerable: false });
        JSON.parse('{}', () => array);
        return array.length === 3 && array[0] === 0 &&
            Object.getOwnPropertyDescriptor(array, '2').enumerable === false;
    })()`), true);
});

test('work exhaustion leaves arrays untouched before committing any deletion', () => {
    const p = page([['JSON_EDIT', args([step('items'), step({ slice: [null, null, 1] })])]]);
    p.install();
    assert.equal(p.run(`(() => {
        const input = { items: new Array(50000).fill(1) };
        return JSON.parse('{}', () => input) === input && input.items.length === 50000;
    })()`), true);
});

test('trusted call directives skip accessor methods without invoking their getters', () => {
    const p = page([['TRUSTED_JSON_EDIT', args([step('ads')],
        ['call', ['${obj}', 'mark', '${key}']])]]); p.install();
    assert.equal(p.run(`(() => {
        let reads = 0;
        const input = { ads: 1, get mark() { reads++; return () => {}; } };
        JSON.parse('{}', () => input); return reads;
    })()`), 0);
});

test('trusted string assignments use captured Date.now without reading page replacements', () => {
    const p = page([['TRUSTED_JSON_EDIT', args([step('time')], ['=', 'at ${now}'])]], {
        before: 'Date.now = () => 1234;',
    }); p.install();
    assert.equal(p.run(`(() => {
        let reads = 0;
        Object.defineProperty(Date, 'now', { get() { reads++; throw new Error('Page getter'); } });
        const value = JSON.parse('{"time":"old"}');
        return value.time === 'at 1234' && reads === 0;
    })()`), true);
});

test('trusted call directives never dispatch explicit evaluators or timer code strings', () => {
    for (const name of ['eval', 'Function', 'execScript', 'setTimeout', 'setInterval']) {
        const p = page([['TRUSTED_JSON_EDIT', args([step('value')],
            ['call', [null, name, 'executed = true']])]], {
            before: 'executed = false;',
        }); p.install();
        p.run(`self[${JSON.stringify(name)}] = () => { executed = true; };`);
        p.run('JSON.parse(\'{"value":1}\')');
        assert.equal(p.run('executed'), false, name);
    }
});

const output = process.env.SCRIPTLET_OUTPUT_DIR;
test('Kotlin-compiled slices and logical expressions retain their full meaning', {
    skip: !output || !existsSync(join(output, 'jsonpath-extensions.json')),
}, () => {
    const cases = JSON.parse(readFileSync(join(output, 'jsonpath-extensions.json'), 'utf8'));
    assert.ok(cases.length >= 20);
    for (const [query, input, expected, source] of cases) {
        const p = page([]); p.run(source);
        const actual = p.run(`JSON.stringify(JSON.parse(${JSON.stringify(input)}))`);
        assert.deepEqual(JSON.parse(actual), JSON.parse(expected), query);
    }
});
