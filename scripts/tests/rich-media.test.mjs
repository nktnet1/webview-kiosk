import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(
  path.resolve('app/src/main/java/uk/nktnet/webviewkiosk/utils/webview/RichMediaScript.kt'), 'utf8',
);
const match = source.match(/internal const val RICH_MEDIA_SCRIPT =\s*"""([\s\S]*?)"""/);
assert.ok(match, 'rich-media document-start script exists');
const richScript = match[1];
new vm.Script(richScript);

const controllerSource = readFileSync(
  path.resolve('app/src/main/java/uk/nktnet/webviewkiosk/utils/webview/BackgroundMediaPlaybackController.kt'), 'utf8',
);
const documentMatch = controllerSource.match(/internal const val DOCUMENT_START_SCRIPT =\s*"""([\s\S]*?)"""/);
assert.ok(documentMatch, 'media bootstrap script exists');
const documentScript = documentMatch[1];

function frame({
  host = 'www.youtube.com', playing = true, title = 'Test song',
  channel = 'Test channel', videoId = 'abcdefghijk', playlist = ['abcdefghijk', 'noprstuvwxy'],
  index = 0, children = [], pathname = '/watch', shortsPreviousEnabled = true,
} = {}) {
  const snapshots = [];
  const events = new Map();
  const windowEvents = new Map();
  const timers = [];
  const commands = [];
  const media = {
    paused: !playing, ended: false, isConnected: true,
    currentTime: 40, duration: 300, playbackRate: 1,
    src: 'https://media.example.test/original.mp4',
    currentSrc: 'https://media.example.test/original.mp4',
    srcObject: null,
    play() { this.paused = false; commands.push('play'); return Promise.resolve(); },
    pause() { this.paused = true; commands.push('pause'); },
  };
  const isShorts = pathname.startsWith('/shorts/');
  const mobileShorts = isShorts && host.startsWith('m.youtube.com');
  const root = {
    querySelector(selector) { return selector === 'video' ? media : null; },
    getVideoData() { return { title, author: channel, video_id: videoId }; },
    getDuration() { return media.duration; },
    getCurrentTime() { return media.currentTime; },
    getPlaylist() { return playlist; },
    getPlaylistIndex() { return index; },
    nextVideo() { commands.push('next'); },
    previousVideo() { commands.push('previous'); },
    seekTo(value) { media.currentTime = value; commands.push(['seek', value]); },
    setPlaybackRate(value) { media.playbackRate = value; commands.push(['speed', value]); },
    playVideo() { media.play(); },
  };
  function controlButton(name) {
    return {
      getAttribute() { return null; },
      disabled: name === 'previous-short' && !shortsPreviousEnabled,
      hasAttribute() { return false; },
      click() { commands.push(`${name}-click`); },
    };
  }
  media.closest = () => root;
  const staleRoot = { getVideoData() { return { title: 'Wrong inactive Short', video_id: 'oldoldold12' }; } };
  let elements = [media];
  const document = {
    title: `${title} - YouTube`,
    getElementById(id) {
      return host.includes('youtube') && id === 'movie_player' ? (isShorts ? staleRoot : root) : null;
    },
    querySelector(selector) {
      if (isShorts && !mobileShorts && selector === 'ytd-reel-video-renderer[is-active] video') return media;
      if (isShorts && !mobileShorts && selector === '#navigation-button-down button') return controlButton('next-short');
      if (isShorts && !mobileShorts && selector === '#navigation-button-up button') return controlButton('previous-short');
      if (host === 'music.youtube.com' && selector === 'ytmusic-player-bar .next-button') return controlButton('next');
      if (host === 'music.youtube.com' && selector === 'ytmusic-player-bar .previous-button') return controlButton('previous');
      if (host.startsWith('m.youtube.com')) return null;
      if (host !== 'music.youtube.com' && selector === '.ytp-next-button') return controlButton('next');
      return null;
    },
    querySelectorAll(selector) {
      if (selector === 'audio,video') return elements;
      if (selector === 'shorts-video video') return mobileShorts ? [media] : [];
      if (selector === '.player-controls-middle .player-middle-controls-prev-next-button') {
        return host === 'm.youtube.com' ? [controlButton('previous'), controlButton('next')] : [];
      }
      if (selector === 'iframe') return children.map((child) => ({ contentWindow: child.window }));
      return [];
    },
    addEventListener(name, fn) { events.set(name, [...(events.get(name) ?? []), fn]); },
  };
  const mediaSession = {
    metadata: null, handlers: {},
    setActionHandler(action, handler) { this.handlers[action] = handler; },
  };
  const window = {
    location: { hostname: host, pathname },
    navigator: { mediaSession },
    WebviewKioskBackgroundMedia: {
      onMediaInfoChanged(id, info) { snapshots.push([id, JSON.parse(info)]); },
      onPlaybackStateChanged() {},
    },
    addEventListener(name, fn) { windowEvents.set(name, [...(windowEvents.get(name) ?? []), fn]); },
    setInterval(fn) { timers.push(fn); },
    postMessage(data) {
      for (const fn of windowEvents.get('message') ?? []) fn({ source: window.parent, data });
    },
  };
  window.parent = window;
  for (const child of children) child.window.parent = window;
  const sandbox = { window, document, navigator: window.navigator, location: window.location, setTimeout, isFinite };
  vm.runInNewContext(documentScript, sandbox);
  vm.runInNewContext(richScript, sandbox);
  return {
    window, snapshots, commands, media, mediaSession, frameId: window.__wvkFrameId,
    replaceMedia(replacement) {
      media.isConnected = false;
      elements = [replacement];
    },
    tick() { for (const fn of timers) fn(); },
    emit(name) { for (const fn of events.get(name) ?? []) fn({ target: media }); },
  };
}

test('YouTube title/channel/thumbnail/duration and playlist actions reach the native bridge', () => {
  const f = frame();
  const info = f.snapshots.at(-1)[1];
  assert.equal(info.title, 'Test song');
  assert.equal(info.artist, 'Test channel');
  assert.equal(info.artworkUrl, 'https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg');
  assert.equal(info.durationMs, 300_000);
  assert.equal(info.positionMs, 40_000);
  assert.equal(info.canNext, true);
  assert.equal(info.canPrevious, false);
  assert.equal(info.canSeek, true);
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  assert.deepEqual(f.commands, ['next']);
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.deepEqual(f.commands, ['next'], 'unsupported previous must do nothing');
});

test('YouTube playlist previous, seekbar, skip and speed control the existing player', () => {
  const f = frame({ index: 1 });
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  f.window.__wvkControlRichMedia('seek', f.frameId, 90_000);
  f.window.__wvkControlRichMedia('rewind', f.frameId, 0);
  f.window.__wvkControlRichMedia('forward', f.frameId, 0);
  f.window.__wvkControlRichMedia('speed', f.frameId, 0);
  assert.deepEqual(f.commands, ['previous', ['seek', 90], ['seek', 80], ['seek', 110], ['speed', 1.25]]);
});

test('ordinary HTML media has no Next/Previous unless the website installs media-session handlers', () => {
  const f = frame({ host: 'music.example.test' });
  assert.equal(f.snapshots.at(-1)[1].canNext, false);
  assert.equal(f.snapshots.at(-1)[1].canPrevious, false);
  let next = 0;
  f.mediaSession.setActionHandler('nexttrack', () => { next++; });
  assert.equal(f.snapshots.at(-1)[1].canNext, true);
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  assert.equal(next, 1);
  f.mediaSession.setActionHandler('nexttrack', null);
  assert.equal(f.snapshots.at(-1)[1].canNext, false);
});

test('targeted transport commands reach only the playing cross-origin child frame', () => {
  const playingChild = frame({ host: 'www.youtube.com', index: 1 });
  const adChild = frame({ host: 'music.example.test', index: 1 });
  const parent = frame({ host: 'example.org', playing: false, children: [playingChild, adChild] });
  parent.window.__wvkControlRichMedia('next', playingChild.frameId, 0);
  assert.deepEqual(playingChild.commands, ['next']);
  assert.deepEqual(adChild.commands, []);
  assert.deepEqual(parent.commands, []);
});

test('progress updates report position changes without an unrelated media switch', () => {
  const f = frame({ host: 'music.example.test' });
  f.media.currentTime = 120;
  f.tick();
  assert.equal(f.snapshots.at(-1)[1].positionMs, 120_000);
  assert.equal(f.snapshots.at(-1)[0], f.frameId);
});

test('commands from unrelated windows cannot control a cross-origin frame', () => {
  const f = frame();
  const before = f.commands.length;
  f.window.postMessage({ __wvkRichMediaCommand: 'next', target: f.frameId, value: 0 });
  assert.equal(f.commands.length, before, 'top-level messages from itself are rejected');
});

test('YouTube watch-page Next uses its enabled UI button when there is no playlist', () => {
  const f = frame({ playlist: [] });
  assert.equal(f.snapshots.at(-1)[1].canNext, true);
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  assert.deepEqual(f.commands, ['next-click']);
});


test('YouTube Music exposes its own next and previous controls without a playlist API', () => {
  const f = frame({ host: 'music.youtube.com', playlist: [] });
  const info = f.snapshots.at(-1)[1];
  assert.equal(info.canNext, true);
  assert.equal(info.canPrevious, true);
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.deepEqual(f.commands, ['next-click', 'previous-click']);
});

test('mobile YouTube identifies separate next and previous buttons by order', () => {
  const f = frame({ host: 'm.youtube.com', playlist: [] });
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.deepEqual(f.commands, ['next-click', 'previous-click']);
});

test('YouTube Shorts metadata and controls use the active reel, not an inactive movie_player', () => {
  const f = frame({
    pathname: '/shorts/zyxwvutsrqp', title: 'Active Short', channel: 'Short creator',
    videoId: '', playlist: ['first-video', 'second-video'], index: 1,
  });
  const info = f.snapshots.at(-1)[1];
  assert.equal(info.title, 'Active Short');
  assert.equal(info.artist, 'Short creator');
  assert.equal(info.artworkUrl, 'https://i.ytimg.com/vi/zyxwvutsrqp/hqdefault.jpg');
  assert.equal(info.positionMs, 40_000);
  assert.equal(info.canNext, true);
  assert.equal(info.canPrevious, true);
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  f.window.__wvkControlRichMedia('seek', f.frameId, 90_000);
  assert.deepEqual(f.commands, ['next-short-click', 'previous-short-click', ['seek', 90]]);
});

test('Shorts hides unavailable Previous controls instead of invoking playlist methods', () => {
  const f = frame({ pathname: '/shorts/zyxwvutsrqp', shortsPreviousEnabled: false });
  assert.equal(f.snapshots.at(-1)[1].canPrevious, false);
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.deepEqual(f.commands, []);
});

test('mobile YouTube Shorts selects the playing shorts-video without advertising missing arrows', () => {
  const f = frame({ host: 'm.youtube.com', pathname: '/shorts/zyxwvutsrqp' });
  const info = f.snapshots.at(-1)[1];
  assert.equal(info.title, 'Test song');
  assert.equal(info.artworkUrl, 'https://i.ytimg.com/vi/zyxwvutsrqp/hqdefault.jpg');
  assert.equal(info.canPrevious, false);
  assert.equal(info.canNext, false);
});

test('notification Play never starts a replacement element that has not played', () => {
  const f = frame({ host: 'music.example.test' });
  f.window.__wvkControlRichMedia('pause', f.frameId, 0);
  const replacement = { ...f.media, isConnected: true };
  f.replaceMedia(replacement);
  f.window.__wvkControlRichMedia('play', f.frameId, 0);
  assert.equal(replacement.paused, true, 'a previous player cannot authorise an unrelated player');
});


test('notification fallback Play rejects a different source on the last played element', () => {
  for (const change of ['src', 'currentSrc', 'srcObject', 'emptied']) {
    const f = frame({ host: 'music.example.test' });
    // A site-triggered pause does not populate our notification pause queue.
    f.media.pause();
    if (change === 'emptied') f.emit('emptied');
    else f.media[change] = change === 'srcObject' ? {} : 'https://media.example.test/new.mp4';
    f.window.__wvkControlRichMedia('play', f.frameId, 0);
    assert.equal(f.media.paused, true, change);
  }
});

test('notification fallback Play still resumes the same source after a site-triggered pause', () => {
  const f = frame({ host: 'music.example.test' });
  f.media.pause();
  f.window.__wvkControlRichMedia('play', f.frameId, 0);
  assert.equal(f.media.paused, false);
});


test('a newly played source becomes eligible for notification fallback Play', () => {
  const f = frame({ host: 'music.example.test' });
  f.media.pause();
  f.media.src = f.media.currentSrc = 'https://media.example.test/new.mp4';
  f.emit('emptied');
  f.media.play();
  f.emit('playing');
  f.media.pause();
  f.window.__wvkControlRichMedia('play', f.frameId, 0);
  assert.equal(f.media.paused, false);
});
