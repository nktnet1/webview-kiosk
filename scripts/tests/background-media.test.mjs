import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';
import vm from 'node:vm';

const controller = readFileSync(
  path.resolve('app/src/main/java/uk/nktnet/webviewkiosk/utils/webview/BackgroundMediaPlaybackController.kt'),
  'utf8',
);

function script(name) {
  const match = controller.match(new RegExp(`internal const val ${name} =\\s*"""([\\s\\S]*?)"""`));
  assert.ok(match, `${name} script must exist`);
  return match[1];
}

function createFrame(hostname, {
  playing = true, children = [], rejectResume = false, requireVisibleOnResume = false,
} = {}) {
  const reports = [];
  const documentHandlers = new Map();
  const windowHandlers = new Map();
  const media = {
    paused: !playing,
    ended: false,
    isConnected: true,
    pause() {
      this.paused = true;
      emit('pause');
    },
    play() {
      if (rejectResume || (requireVisibleOnResume && document.hidden)) {
        return Promise.reject(new Error('not allowed'));
      }
      this.paused = false;
      emit('playing');
      return Promise.resolve();
    },
  };
  function emit(name) {
    for (const listener of documentHandlers.get(name) ?? []) listener();
  }
  const document = {
    querySelectorAll(selector) {
      if (selector === 'audio,video') return [media];
      if (selector === 'iframe') return children.map((child) => ({ contentWindow: child.window }));
      return [];
    },
    addEventListener(name, fn) {
      documentHandlers.set(name, [...(documentHandlers.get(name) ?? []), fn]);
    },
  };
  let nativeHidden = false;
  Object.defineProperties(document, {
    hidden: { configurable: true, get() { return nativeHidden; } },
    visibilityState: { configurable: true, get() { return nativeHidden ? 'hidden' : 'visible'; } },
  });
  const window = {
    location: { hostname },
    WebviewKioskBackgroundMedia: {
      onPlaybackStateChanged(id, isPlaying) { reports.push([id, isPlaying]); },
    },
    addEventListener(name, fn) {
      windowHandlers.set(name, [...(windowHandlers.get(name) ?? []), fn]);
    },
    postMessage(data) {
      for (const listener of windowHandlers.get('message') ?? []) {
        listener({ data, source: window.parent });
      }
    },
  };
  window.parent = window;
  for (const child of children) child.window.parent = window;
  const sandbox = { window, document };
  vm.runInNewContext(script('DOCUMENT_START_SCRIPT'), sandbox);
  function visibilityChange() {
    const event = {
      blocked: false,
      stopImmediatePropagation() { this.blocked = true; },
    };
    for (const listener of windowHandlers.get('visibilitychange') ?? []) {
      listener(event);
      if (event.blocked) return event;
    }
    for (const listener of documentHandlers.get('visibilitychange') ?? []) {
      listener(event);
      if (event.blocked) return event;
    }
    return event;
  }
  return {
    window, document, media, reports, sandbox, visibilityChange,
    hideNative() { nativeHidden = true; },
  };
}

test('notification Pause then Play affects only media that was playing', () => {
  const frame = createFrame('example.com');
  assert.equal(frame.reports.at(-1)[1], true);
  vm.runInNewContext(script('PAUSE_FOR_NOTIFICATION_SCRIPT'), frame.sandbox);
  assert.equal(frame.media.paused, true);
  assert.equal(frame.reports.at(-1)[1], false);
  vm.runInNewContext(script('RESUME_MEDIA_SCRIPT'), frame.sandbox);
  assert.equal(frame.media.paused, false);
  assert.equal(frame.reports.at(-1)[1], true);
});

test('Play does not start a player that was already paused', () => {
  const frame = createFrame('example.com', { playing: false });
  vm.runInNewContext(script('PAUSE_FOR_NOTIFICATION_SCRIPT'), frame.sandbox);
  vm.runInNewContext(script('RESUME_MEDIA_SCRIPT'), frame.sandbox);
  assert.equal(frame.media.paused, true);
});

test('controls reach cross-origin frames and Stop clears pending resume', () => {
  const child = createFrame('independent.example');
  const parent = createFrame('example.com', { playing: false, children: [child] });
  vm.runInNewContext(script('PAUSE_FOR_NOTIFICATION_SCRIPT'), parent.sandbox);
  assert.equal(child.media.paused, true);
  vm.runInNewContext(script('PAUSE_MEDIA_SCRIPT'), parent.sandbox);
  vm.runInNewContext(script('RESUME_MEDIA_SCRIPT'), parent.sandbox);
  assert.equal(child.media.paused, true);
});

test('YouTube and YouTube-nocookie playback is tracked and controlled', () => {
  for (const host of ['youtube.com', 'm.youtube.com', 'music.youtube.com', 'www.youtube-nocookie.com']) {
    const frame = createFrame(host);
    assert.equal(frame.reports.at(-1)[1], true, host);
    vm.runInNewContext(script('PAUSE_FOR_NOTIFICATION_SCRIPT'), frame.sandbox);
    assert.equal(frame.media.paused, true, host);
    vm.runInNewContext(script('RESUME_MEDIA_SCRIPT'), frame.sandbox);
    assert.equal(frame.media.paused, false, host);
  }
});

test('notification controls reach a cross-origin embedded YouTube player', () => {
  const embed = createFrame('www.youtube-nocookie.com');
  const parent = createFrame('example.com', { playing: false, children: [embed] });
  vm.runInNewContext(script('PAUSE_FOR_NOTIFICATION_SCRIPT'), parent.sandbox);
  assert.equal(embed.media.paused, true);
  vm.runInNewContext(script('RESUME_MEDIA_SCRIPT'), parent.sandbox);
  assert.equal(embed.media.paused, false);
});

test('YouTube visibility signals are suppressed during active playback only', () => {
  const frame = createFrame('music.youtube.com');
  frame.hideNative();
  assert.equal(frame.document.hidden, false);
  assert.equal(frame.document.visibilityState, 'visible');
  assert.equal(frame.visibilityChange().blocked, true);

  vm.runInNewContext(script('PAUSE_FOR_NOTIFICATION_SCRIPT'), frame.sandbox);
  assert.equal(frame.document.hidden, true);
  assert.equal(frame.document.visibilityState, 'hidden');
  assert.equal(frame.visibilityChange().blocked, false);
});

test('YouTube notification Play can resume while the native page is hidden', async () => {
  const frame = createFrame('www.youtube.com', { requireVisibleOnResume: true });
  frame.hideNative();
  vm.runInNewContext(script('PAUSE_FOR_NOTIFICATION_SCRIPT'), frame.sandbox);
  assert.equal(frame.document.hidden, true);
  vm.runInNewContext(script('RESUME_MEDIA_SCRIPT'), frame.sandbox);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(frame.media.paused, false);
  assert.equal(frame.document.hidden, false);
});

test('visibility events on unrelated sites are not suppressed', () => {
  const frame = createFrame('example.com');
  frame.hideNative();
  assert.equal(frame.document.hidden, true);
  assert.equal(frame.document.visibilityState, 'hidden');
  assert.equal(frame.visibilityChange().blocked, false);
});

test('cross-origin media controls reject messages from unrelated frames', () => {
  const frame = createFrame('example.com');
  frame.window.postMessage({ __wvkMediaAction: 'stop' });
  assert.equal(frame.media.paused, false);
});

test('a rejected resume does not generate a false playing report', async () => {
  const frame = createFrame('example.com', { rejectResume: true });
  vm.runInNewContext(script('PAUSE_FOR_NOTIFICATION_SCRIPT'), frame.sandbox);
  vm.runInNewContext(script('RESUME_MEDIA_SCRIPT'), frame.sandbox);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(frame.media.paused, true);
  assert.equal(frame.reports.at(-1)[1], false);
});
