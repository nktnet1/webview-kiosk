import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { test } from 'node:test';
import { page } from './scriptlet-fixture.mjs';

const suffixes = ['*.ck\n*.compute.amazonaws.com\nco.uk\ncom\ngithub.io\n', 'www.ck\n'];
test('entity routing keeps exclusions and both exception types scoped to the frame', () => {
    const rules = [
        ['SET', ['marker', 'false'], 'ticketmaster.*', ['safe.ticketmaster.*']],
        ['SET', ['marker', 'false'], 'other.example'],
        ['SET', ['marker', 'false'], 'skip.ticketmaster.*', [], true],
        [null, [], 'ticketmaster.co.uk', [], true],
    ];
    for (const [host, count] of [
        ['ticketmaster.com', 1], ['sub.ticketmaster.github.io', 1],
        ['ticketmaster.a.ck', 1], ['ticketmaster.www.ck', 0],
        ['ticketmaster.compute.amazonaws.com', 0],
        ['ticketmaster.a.compute.amazonaws.com', 1], ['other.example', 1],
        ['safe.ticketmaster.com', 0], ['skip.ticketmaster.com', 0],
        ['ticketmaster.co.uk', 0], ['ticketmaster.com.attacker.com', 0],
    ]) {
        const p = page(rules, { host, suffixes }); p.install();
        assert.equal(p.run('typeof marker === "boolean"') ? 1 : 0, count, host);
    }
});

test('entity exceptions with exclusions do not become blanket exceptions', () => {
    const rules = [
        ['SET', ['marker', 'false'], 'ticketmaster.*'],
        [null, [], '', ['ticketmaster.*'], true],
        [null, [], 'safe.ticketmaster.*', [], true],
    ];
    for (const [host, enabled] of [['ticketmaster.com', true], ['safe.ticketmaster.com', false]]) {
        const p = page(rules, { host, suffixes }); p.install();
        assert.equal(p.run('typeof marker === "boolean"'), enabled);
    }
});

test('entity gates leave unrelated frames before parsing any routing data', () => {
    const p = page([['SET', ['marker', 'false'], 'ticketmaster.*']],
        { host: 'unrelated.com', suffixes, before: 'JSON.parse = () => { throw "parsed"; };' });
    assert.doesNotThrow(() => p.install());
    assert.equal(p.run('typeof marker'), 'undefined');
});

test('entity-scoped GPC rules run synchronously in a child frame without reading its parent', () => {
    const p = page([
        ['SET', ['Navigator.prototype.globalPrivacyControl', 'false'], 'ticketmaster.*'],
        ['SET', ['navigator.globalPrivacyControl', 'false'], 'ticketmaster.*'],
    ], { host: 'checkout.ticketmaster.co.uk', suffixes, before: `
        function Navigator() {}
        Object.defineProperty(Navigator, 'prototype', { writable: false });
        navigator = new Navigator();
        Object.defineProperty(self, 'top', { get() { throw new Error('Cross-origin frame'); } });
    ` });
    p.install();
    assert.equal(p.run('navigator.globalPrivacyControl'), false);
    assert.equal(p.run('Navigator.prototype.globalPrivacyControl'), false);
});

const output = process.env.SCRIPTLET_OUTPUT_DIR;
test('Kotlin-produced dispatcher agrees with OkHttp across the resolved PSL', { skip: !output }, () => {
    const source = readFileSync(join(output, 'entity-injection.js'), 'utf8');
    const hosts = JSON.parse(readFileSync(join(output, 'entity-hosts.json'), 'utf8'));
    assert.ok(hosts.length > 10000);
    for (const [host, count] of hosts) {
        const p = page([], { host });
        p.run(source);
        assert.equal(p.run('typeof entityMarker === "boolean"') ? 1 : 0, count, host);
    }
});
