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
  pathname = '/watch',
} = {}) {
  const reports = [];
  const userInteractions = [];
  const documentHandlers = new Map();
  const windowHandlers = new Map();
  const intervals = [];
  let selectedVideo;
  const media = {
    paused: !playing,
    ended: false,
    isConnected: true,
    src: 'https://media.example.test/original.mp4',
    currentSrc: 'https://media.example.test/original.mp4',
    srcObject: null,
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
  selectedVideo = media;
  function emit(name) {
    for (const listener of documentHandlers.get(name) ?? []) listener({ target: media });
  }
  const youtubePlayer = {
    contains(target) { return target === selectedVideo; },
    querySelector(selector) { return selector === 'video' ? selectedVideo : null; },
    getPlayerState() { return selectedVideo.paused ? (selectedVideo.ended ? 0 : 2) : 1; },
    playVideo() { const result = selectedVideo.play(); if (result?.catch) result.catch(() => {}); },
  };
  const isShorts = pathname.startsWith('/shorts/');
  const mobileShorts = isShorts && hostname.startsWith('m.youtube.com');
  media.closest = () => youtubePlayer;
  const stalePlayer = {
    getPlayerState() { return 2; },
    playVideo() { throw new Error('Inactive Shorts player used'); },
  };
  const document = {
    getElementById(id) { return id === 'movie_player' ? (isShorts ? stalePlayer : youtubePlayer) : null; },
    querySelector(selector) {
      if (isShorts && !mobileShorts && selector === 'ytd-reel-video-renderer[is-active] video') return selectedVideo;
      return selector === 'video' ? selectedVideo : null;
    },
    querySelectorAll(selector) {
      if (selector === 'audio,video') return [selectedVideo];
      if (selector === 'shorts-video video') return mobileShorts ? [selectedVideo] : [];
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
    location: { hostname, pathname },
    WebviewKioskBackgroundMedia: {
      onPlaybackStateChanged(id, isPlaying) { reports.push([id, isPlaying]); },
      canAutoResume() { return true; },
      onUserInteraction() { userInteractions.push(true); },
    },
    addEventListener(name, fn) {
      windowHandlers.set(name, [...(windowHandlers.get(name) ?? []), fn]);
    },
    navigator: { mediaSession: { handlers: {}, setActionHandler(name, handler) { this.handlers[name] = handler; } } },
    setInterval(fn) { intervals.push(fn); },
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
  vm.runInNewContext(script('YOUTUBE_BACKGROUND_RECOVERY_SCRIPT'), sandbox);
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
    window, document, media, reports, userInteractions, sandbox, visibilityChange,
    gesture(type, trusted) {
      for (const listener of documentHandlers.get(type) ?? []) {
        listener({ target: media, isTrusted: trusted });
      }
    },
    replaceVideo(replacement) {
      media.isConnected = false;
      selectedVideo = replacement;
    },
    emit,
    tick() { for (const fn of intervals) fn(); },
    hideNative() { nativeHidden = true; },
  };
}

test('foreground playback refresh reaches already-playing nested frames', () => {
  const embed = createFrame('player.example');
  const child = createFrame('embed.example', { playing: false, children: [embed] });
  const parent = createFrame('example.com', { playing: false, children: [child] });
  for (const frame of [parent, child, embed]) frame.reports.length = 0;

  parent.window.__wvkReportMediaPlayback();

  assert.equal(parent.reports.length, 1);
  assert.equal(parent.reports[0][1], false);
  assert.equal(child.reports.length, 1);
  assert.equal(child.reports[0][1], false);
  assert.equal(embed.reports.length, 1);
  assert.equal(embed.reports[0][1], true);
});

test('playback refresh rejects requests from unrelated windows', () => {
  const frame = createFrame('example.com');
  frame.reports.length = 0;
  frame.window.postMessage({ __wvkMediaReport: true });
  assert.equal(frame.reports.length, 0);
});

test('real page gestures can rearm media after notification Stop, synthetic events cannot', () => {
  const frame = createFrame('example.com');
  frame.window.__wvkControlMedia('stop');
  frame.gesture('pointerdown', false);
  frame.gesture('click', false);
  assert.equal(frame.userInteractions.length, 0);
  frame.gesture('pointerdown', true);
  assert.equal(frame.userInteractions.length, 1);
  frame.gesture('keydown', true);
  assert.equal(frame.userInteractions.length, 2);
});

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


test('Home-triggered YouTube pause is recovered without losing playback visibility', () => {
  const frame = createFrame('m.youtube.com');
  frame.tick();
  frame.window.__wvkSetHostBackground(true);
  frame.hideNative();
  frame.media.pause();
  assert.equal(frame.reports.at(-1)[1], false);
  assert.equal(frame.document.hidden, false);
  frame.tick();
  assert.equal(frame.media.paused, false);
  assert.equal(frame.reports.at(-1)[1], true);
});

test('YouTube deliberately paused before Home stays paused', () => {
  const frame = createFrame('music.youtube.com');
  frame.media.pause();
  frame.window.__wvkSetHostBackground(true);
  frame.tick();
  frame.tick();
  assert.equal(frame.media.paused, true);
});

test('notification and media-session Pause must not trigger YouTube auto-resume', () => {
  const frame = createFrame('www.youtube.com');
  frame.window.__wvkSetHostBackground(true);
  vm.runInNewContext(script('PAUSE_FOR_NOTIFICATION_SCRIPT'), frame.sandbox);
  frame.tick();
  assert.equal(frame.media.paused, true);

  const alternate = createFrame('m.youtube.com');
  const session = alternate.window.navigator.mediaSession;
  let called = false;
  session.setActionHandler('pause', () => { called = true; alternate.media.pause(); });
  alternate.window.__wvkSetHostBackground(true);
  session.handlers.pause({});
  alternate.tick();
  assert.equal(called, true);
  assert.equal(alternate.media.paused, true);
});

test('YouTube never auto-restarts an ended video or a video that was never playing', () => {
  const ended = createFrame('youtube.com');
  ended.window.__wvkSetHostBackground(true);
  ended.media.ended = true;
  ended.media.pause();
  ended.tick();
  assert.equal(ended.media.paused, true);
  const idle = createFrame('m.youtube.com', { playing: false });
  idle.window.__wvkSetHostBackground(true);
  idle.tick();
  assert.equal(idle.media.paused, true);
});

test('background status is forwarded into an embedded YouTube frame', () => {
  const embed = createFrame('www.youtube-nocookie.com');
  const parent = createFrame('example.com', { playing: false, children: [embed] });
  parent.window.__wvkSetHostBackground(true);
  embed.media.pause();
  embed.tick();
  assert.equal(embed.media.paused, false);
});

test('YouTube recovery ends after finite attempts rather than looping endlessly', () => {
  const frame = createFrame('music.youtube.com', { rejectResume: true });
  frame.window.__wvkSetHostBackground(true);
  frame.media.pause();
  for (let i = 0; i < 12; i++) frame.tick();
  assert.equal(frame.media.paused, true);
  frame.hideNative();
  assert.equal(frame.document.hidden, true);
});


test('an active music interruption prevents automatic YouTube resume', () => {
  const frame = createFrame('music.youtube.com');
  frame.window.WebviewKioskBackgroundMedia.canAutoResume = () => false;
  frame.window.__wvkSetHostBackground(true);
  frame.media.pause();
  for (let i = 0; i < 10; i++) frame.tick();
  assert.equal(frame.media.paused, true);
  frame.hideNative();
  assert.equal(frame.document.hidden, true);
});

test('YouTube abandons automatic recovery after a sustained audio interruption', () => {
  const frame = createFrame('www.youtube.com');
  frame.window.__wvkSetHostBackground(true);
  frame.media.pause();
  let audioAvailable = false;
  frame.window.WebviewKioskBackgroundMedia.canAutoResume = () => audioAvailable;
  for (let i = 0; i < 8; i++) frame.tick();
  assert.equal(frame.media.paused, true);

  audioAvailable = true;
  for (let i = 0; i < 3; i++) frame.tick();
  assert.equal(frame.media.paused, true, 'ending the interruption must not revive an expired recovery');

  frame.window.__wvkControlMedia('resume');
  frame.tick();
  assert.equal(frame.media.paused, false, 'an explicit Play can start a new recovery budget');
});

test('YouTube can still recover after a brief audio interruption', () => {
  const frame = createFrame('www.youtube.com');
  frame.window.__wvkSetHostBackground(true);
  frame.media.pause();
  frame.window.WebviewKioskBackgroundMedia.canAutoResume = () => false;
  frame.tick();
  assert.equal(frame.media.paused, true);

  frame.window.WebviewKioskBackgroundMedia.canAutoResume = () => true;
  frame.tick();
  assert.equal(frame.media.paused, false);
});

test('YouTube Shorts recover the active reel after Home without resuming the inactive player', () => {
  const frame = createFrame('www.youtube.com', { pathname: '/shorts/zyxwvutsrqp' });
  frame.window.__wvkSetHostBackground(true);
  frame.media.pause();
  frame.tick();
  assert.equal(frame.media.paused, false);
  assert.equal(frame.reports.at(-1)[1], true);
});

test('YouTube Shorts paused intentionally before Home stay paused', () => {
  const frame = createFrame('www.youtube.com', { pathname: '/shorts/zyxwvutsrqp' });
  frame.media.pause();
  frame.window.__wvkSetHostBackground(true);
  frame.tick();
  assert.equal(frame.media.paused, true);
});

test('mobile YouTube Shorts recover the selected shorts-video after Home', () => {
  const frame = createFrame('m.youtube.com', { pathname: '/shorts/zyxwvutsrqp' });
  frame.window.__wvkSetHostBackground(true);
  frame.media.pause();
  frame.tick();
  assert.equal(frame.media.paused, false);
});


test('an old rejected Play cannot restore the resume queue after Stop', async () => {
  const frame = createFrame('example.com');
  frame.window.__wvkControlMedia('pause');
  const originalPlay = frame.media.play;
  let rejectPlay;
  frame.media.play = () => new Promise((resolve, reject) => { rejectPlay = reject; });
  frame.window.__wvkControlMedia('resume');
  frame.window.__wvkControlMedia('stop');
  rejectPlay(new Error('play interrupted by Stop'));
  await new Promise((resolve) => setImmediate(resolve));
  frame.media.play = originalPlay;
  frame.window.__wvkControlMedia('resume');
  assert.equal(frame.media.paused, true);
});

test('a rejected current Play remains available for a later retry', async () => {
  const frame = createFrame('example.com');
  frame.window.__wvkControlMedia('pause');
  const originalPlay = frame.media.play;
  frame.media.play = () => Promise.reject(new Error('not allowed yet'));
  frame.window.__wvkControlMedia('resume');
  await new Promise((resolve) => setImmediate(resolve));
  frame.media.play = originalPlay;
  frame.window.__wvkControlMedia('resume');
  assert.equal(frame.media.paused, false);
});

test('notification Play cannot resume a different source on the same element', () => {
  for (const change of ['src', 'currentSrc', 'srcObject']) {
    const frame = createFrame('example.com');
    frame.window.__wvkControlMedia('pause');
    frame.media[change] = change === 'srcObject' ? {} : 'https://media.example.test/new.mp4';
    frame.window.__wvkControlMedia('resume');
    assert.equal(frame.media.paused, true, change);
  }
});

test('reloading a paused source invalidates notification resume even at the same URL', () => {
  const frame = createFrame('example.com');
  frame.window.__wvkControlMedia('pause');
  frame.emit('emptied');
  frame.window.__wvkControlMedia('resume');
  assert.equal(frame.media.paused, true);
});

test('YouTube recovery never starts a newly selected, never-played video element', () => {
  for (const pathname of ['/watch', '/shorts/zyxwvutsrqp']) {
    const frame = createFrame('www.youtube.com', { pathname });
    frame.window.__wvkSetHostBackground(true);
    frame.media.pause();
    const replacement = { ...frame.media, isConnected: true };
    frame.replaceVideo(replacement);
    frame.tick();
    assert.equal(replacement.paused, true, pathname);
    frame.hideNative();
    assert.equal(frame.document.hidden, true, 'an unplayed replacement must not retain visibility');
  }
});

test('YouTube recovery rejects changed or reloaded sources on the existing player', () => {
  for (const change of ['src', 'currentSrc', 'srcObject', 'emptied']) {
    const frame = createFrame('www.youtube.com');
    frame.window.__wvkSetHostBackground(true);
    frame.media.pause();
    if (change === 'emptied') frame.emit('emptied');
    else frame.media[change] = change === 'srcObject' ? {} : 'https://media.example.test/new.mp4';
    frame.tick();
    assert.equal(frame.media.paused, true, change);
  }
});


test('an older Play completion cannot clear visibility needed by a newer pending Play', async () => {
  const frame = createFrame('www.youtube.com');
  frame.hideNative();
  frame.window.__wvkControlMedia('pause');
  const settle = [];
  frame.media.play = () => new Promise((resolve) => { settle.push(resolve); });
  frame.window.__wvkControlMedia('resume');
  frame.window.__wvkControlMedia('stop');
  // A fresh playback session is paused while the first play promise is pending.
  frame.media.paused = false;
  frame.window.__wvkControlMedia('pause');
  frame.window.__wvkControlMedia('resume');
  assert.equal(settle.length, 2);
  settle[0]();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(frame.document.hidden, false);
  settle[1]();
  await new Promise((resolve) => setImmediate(resolve));
});


test('a newly played source can subsequently recover after a background pause', () => {
  const frame = createFrame('www.youtube.com');
  frame.window.__wvkSetHostBackground(true);
  frame.media.pause();
  frame.media.src = frame.media.currentSrc = 'https://media.example.test/new.mp4';
  frame.emit('emptied');
  frame.media.play();
  frame.media.pause();
  frame.tick();
  assert.equal(frame.media.paused, false);
});

test('media errors stop reporting playback instead of leaving the notification clock running', () => {
  const frame = createFrame('example.com');
  assert.equal(frame.reports.at(-1)[1], true);
  frame.media.error = { code: 2 };
  frame.emit('error');
  assert.equal(frame.reports.at(-1)[1], false);
});
