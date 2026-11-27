import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const script = readFileSync(new URL('../../app/src/main/assets/image-refresh.js', import.meta.url), 'utf8');
const base = 'https://example.test/gallery/';
const parameter = '_webviewkiosk_refresh';
const absolute = path => new URL(path, base).href;

function collection(elements) {
  // Older WebViews expose indexed DOM collections without Symbol.iterator.
  return Object.assign({ length: elements.length }, elements);
}

function element(attributes = {}) {
  const changes = [];
  return {
    changes,
    getAttribute(name) { return attributes[name] ?? null; },
    setAttribute(name, value) { attributes[name] = value; changes.push([name, value]); },
  };
}

function image(attributes, selected, sources) {
  const node = element(attributes);
  Object.defineProperty(node, 'src', { get() {
    return node.getAttribute('src') ? absolute(node.getAttribute('src')) : '';
  } });
  return Object.assign(node, {
    ownerDocument: { baseURI: base },
    currentSrc: selected ? absolute(selected) : '',
    parentElement: sources ? { tagName: 'PICTURE', querySelectorAll: () => collection(sources) } : null,
  });
}

function refresh(target, ...images) {
  return runInNewContext(`${script.trim()}(${JSON.stringify(target)})`, {
    document: { images: collection(images) }, URL,
    Date: { now: () => 123 }, Math: { random: () => 0.5 },
  });
}

const token = '123-i';
const suffix = `${parameter}=${token}`;

test('refreshes matching duplicate images without relying on load status or reloading the page', () => {
  const first = image({ src: 'photo.jpg' }, 'photo.jpg');
  const failed = image({ src: 'photo.jpg' });
  const other = image({ src: 'other.jpg' }, 'other.jpg');
  assert.equal(refresh(absolute('photo.jpg'), first, failed, other), true);
  for (const node of [first, failed]) {
    assert.deepEqual(node.changes, [['src', absolute(`photo.jpg?${suffix}`)]]);
  }
  assert.deepEqual(other.changes, []);
});

test('preserves raw query encoding, repeated keys, empty fields and fragments', () => {
  const original = `photo.jpg?a=a%20b&a=abc~def&&crop=10,20&${parameter}=old&flag#section`;
  const node = image({ src: original }, original);
  assert.equal(refresh(absolute(original), node), true);
  assert.equal(node.getAttribute('src'), absolute(`photo.jpg?a=a%20b&a=abc~def&&crop=10,20&flag&${suffix}#section`));
});

test('refreshes comma-containing srcset URLs without altering descriptors or fallback', () => {
  const original = '/photo?crop=10,20';
  const node = image({ src: 'fallback.jpg', srcset: `  ${original} 1x,\n /large.jpg 2x` }, original);
  assert.equal(refresh(absolute(original), node), true);
  assert.deepEqual(node.changes, [['srcset', `  ${absolute(`${original}&${suffix}`)} 1x,\n /large.jpg 2x`]]);
});

test('does not refresh an unrelated active image just because it shares the fallback URL', () => {
  const first = image({ src: 'shared.jpg' }, 'shared.jpg');
  const second = image({ src: 'shared.jpg', srcset: 'other.jpg 2x' }, 'other.jpg');
  assert.equal(refresh(absolute('shared.jpg'), first, second), true);
  assert.deepEqual(second.changes, []);
});

test('retries the failed responsive source when WebView reports its fallback URL', () => {
  const source = element({ srcset: 'retina.jpg 2x' });
  const broken = Object.assign(image({ src: 'fallback.jpg' }, 'retina.jpg', [source]), {
    complete: true, naturalWidth: 0,
  });
  const loaded = Object.assign(image({ src: 'fallback.jpg', srcset: 'other.jpg 2x' }, 'other.jpg'), {
    complete: true, naturalWidth: 200,
  });
  assert.equal(refresh(absolute('fallback.jpg'), broken, loaded), true);
  assert.deepEqual(source.changes, [['srcset', `${absolute(`retina.jpg?${suffix}`)} 2x`]]);
  assert.deepEqual(broken.changes, []);
  assert.deepEqual(loaded.changes, []);
});

test('updates only matching picture and img candidates, including repeated matches and matching fallback', () => {
  const source = element({ srcset: 'photo.jpg 480w, large.jpg 960w' });
  const other = element({ srcset: 'other.jpg 1x' });
  const node = image({ src: 'photo.jpg', srcset: 'photo.jpg, /gallery/photo.jpg 2x, other.jpg 3x' }, 'photo.jpg', [source, other]);
  const replacement = absolute(`photo.jpg?${suffix}`);
  assert.equal(refresh(absolute('photo.jpg'), node), true);
  assert.deepEqual(source.changes, [['srcset', `${replacement} 480w, large.jpg 960w`]]);
  assert.deepEqual(other.changes, []);
  assert.deepEqual(node.changes, [['srcset', `${replacement}, ${replacement} 2x, other.jpg 3x`], ['src', replacement]]);
});

test('preserves commas inside data URLs and descriptor parentheses when scanning later candidates', () => {
  const srcset = 'data:image/png;base64,AAAA 1x, broken.jpg unsupported(a,b), photo.jpg 2x';
  const node = image({ src: 'fallback.jpg', srcset }, 'photo.jpg');
  assert.equal(refresh(absolute('photo.jpg'), node), true);
  assert.equal(node.getAttribute('srcset'), srcset.replace('photo.jpg', absolute(`photo.jpg?${suffix}`)));
});

test('does not change signed URL bytes or add an invalidating cache-busting parameter', () => {
  for (const key of ['X-Amz-Signature', 'X-Goog-Signature', 'sig', '%53ignature', 'hdnts']) {
    const original = `photo.jpg?${key}=a%2Bb&key=abc~def`;
    const node = image({ src: original, srcset: `${original} 1x, other.jpg 2x` }, original);
    assert.equal(refresh(absolute(original), node), true);
    assert.deepEqual(node.changes, [['srcset', `${original} 1x, other.jpg 2x`], ['src', original]]);
  }
});

test('retries non-HTTP URLs unchanged, including data URLs in responsive sources', () => {
  for (const original of ['data:image/png;base64,AAAA', 'blob:https://example.test/123', 'file:///tmp/photo.jpg']) {
    const node = image({ src: original, srcset: `${original} 1x, other.jpg 2x` }, original);
    assert.equal(refresh(original, node), true);
    assert.deepEqual(node.changes, [['srcset', `${original} 1x, other.jpg 2x`], ['src', original]]);
  }
});

test('reports no update when the active URL no longer exists in the image attributes', () => {
  const node = image({ src: 'fallback.jpg', srcset: 'new.jpg 2x' }, 'old.jpg');
  assert.equal(refresh(absolute('old.jpg'), node), false);
  assert.deepEqual(node.changes, []);
  assert.equal(refresh('http://[', node), false);
  assert.equal(refresh('', node), false);
});

test('repeated refreshes replace only the existing cache-busting parameter', () => {
  const node = image({ src: `photo.jpg?${parameter}=old&quality=80` });
  assert.equal(refresh(absolute(node.getAttribute('src')), node), true);
  assert.equal(node.getAttribute('src'), absolute(`photo.jpg?quality=80&${suffix}`));
  assert.equal(refresh(absolute(node.getAttribute('src')), node), true);
  assert.equal(node.getAttribute('src'), absolute(`photo.jpg?quality=80&${suffix}`));
});
