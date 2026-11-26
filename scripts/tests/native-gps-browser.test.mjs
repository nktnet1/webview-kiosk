import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { after, before, test } from 'node:test';

const options = { skip: !process.env.SCRIPTLET_CHROMIUM };
const script = readFileSync(new URL('../../app/src/main/assets/native-gps.js', import.meta.url), 'utf8');
let browser;
before(async () => {
  if (options.skip) return;
  const { chromium } = createRequire(import.meta.url)('playwright');
  browser = await chromium.launch({ executablePath: process.env.SCRIPTLET_CHROMIUM, args: ['--no-sandbox'] });
});
after(async () => { await browser?.close(); });

async function fixture(policy = 'geolocation=(self)') {
  const context = await browser.newContext({ geolocation: { latitude: 1, longitude: 2 } });
  await context.grantPermissions(['geolocation'], { origin: 'https://gps.example.test' });
  await context.route('https://gps.example.test/**', route => route.fulfill({
    status: 200, contentType: 'text/html', headers: { 'Permissions-Policy': policy },
    body: '<!doctype html><html><body>GPS fixture</body></html>',
  }));
  await context.addInitScript({ content: `
    window.nativeMessages = [];
    window.browserWatches = 0;
    window.browserClears = 0;
    window.values = [];
    window.errors = [];
    const originalWatch = navigator.geolocation.watchPosition.bind(navigator.geolocation);
    const originalClear = navigator.geolocation.clearWatch.bind(navigator.geolocation);
    navigator.geolocation.watchPosition = (...args) => {
      browserWatches++;
      return originalWatch(...args);
    };
    navigator.geolocation.clearWatch = id => { browserClears++; originalClear(id); };
    window.nativeReply = data => WebviewKioskGps.onmessage({
      data: JSON.stringify({ token: 'browser-document', ...data }),
    });
    window.WebviewKioskGps = { postMessage(raw) {
      const message = JSON.parse(raw);
      nativeMessages.push(message);
      if (message.type === 'hello') queueMicrotask(() => nativeReply({
        type: 'hello', nonce: message.nonce, visible: true,
      }));
      if (message.type === 'validate') queueMicrotask(() => nativeReply({
        type: 'validated', id: message.id, validationId: message.validationId, allowed: true,
      }));
    } };
    ${script}
  ` });
  const page = await context.newPage();
  await page.goto('https://gps.example.test/');
  return { page, context };
}

test('real Chromium Permissions Policy denies the original probe before GPS can subscribe', options, async () => {
  const { page, context } = await fixture('geolocation=()');
  try {
    await page.evaluate(() => navigator.geolocation.watchPosition(
      value => values.push(value), error => errors.push({ code: error.code }),
    ));
    await page.waitForFunction(() => errors.length === 1);
    assert.equal(await page.evaluate(() => errors[0].code), 1);
    assert.equal(await page.evaluate(() => nativeMessages.some(m => m.type === 'subscribe')), false);
    assert.equal(await page.evaluate(() => values.length), 0);
    assert.equal(await page.evaluate(() => browserWatches), 1);
  } finally { await context.close(); }
});

test('real browser geolocation accepts the hook and receives native fixes after authorization', options, async () => {
  const { page, context } = await fixture();
  try {
    await page.evaluate(() => {
      window.watchId = navigator.geolocation.watchPosition(
        value => values.push(value.toJSON()), error => errors.push(error.code),
      );
    });
    await page.waitForFunction(() => browserWatches === 1);
    assert.equal(await page.evaluate(() => values.length), 0, 'The browser fix is not the native GPS result');
    await page.evaluate(() => nativeReply({ type: 'permission', allowed: true }));
    await page.waitForFunction(() => nativeMessages.some(m => m.type === 'subscribe'));
    assert.equal(await page.evaluate(() => browserClears), 1);
    await page.evaluate(() => nativeReply({ type: 'position', ageMs: 0, timestamp: 1700000000000,
      coords: { latitude: 12.3, longitude: 45.6, accuracy: 7, altitude: null,
        altitudeAccuracy: null, heading: null, speed: null },
    }));
    await page.waitForFunction(() => values.length === 1);
    assert.equal(await page.evaluate(() => values[0].coords.latitude), 12.3);
    assert.equal(await page.evaluate(() => errors.length), 0);
    await page.evaluate(() => navigator.geolocation.clearWatch(watchId));
    await page.waitForFunction(() => nativeMessages.at(-1).type === 'unsubscribe');
  } finally { await context.close(); }
});

test('embedded frames retain browser geolocation and do not open a native session', options, async () => {
  const { page, context } = await fixture();
  try {
    await page.evaluate(() => {
      const frame = document.createElement('iframe');
      frame.src = '/child';
      document.body.append(frame);
    });
    await page.waitForFunction(() => document.querySelector('iframe')?.contentWindow?.nativeMessages);
    const child = page.frames().find(frame => frame !== page.mainFrame());
    assert.equal(await child.evaluate(() => Object.hasOwn(navigator.geolocation, 'getCurrentPosition')), false);
    assert.equal(await child.evaluate(() => nativeMessages.length), 0);
  } finally { await context.close(); }
});
