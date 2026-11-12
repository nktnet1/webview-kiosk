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

const recoveryMatch = controllerSource.match(/internal const val YOUTUBE_BACKGROUND_RECOVERY_SCRIPT =\s*"""([\s\S]*?)"""/);
assert.ok(recoveryMatch, 'YouTube recovery script exists');
const recoveryScript = recoveryMatch[1];

function frame({
  host = 'www.youtube.com', playing = true, title = 'Test song',
  channel = 'Test channel', videoId = 'abcdefghijk', playlist = ['abcdefghijk', 'noprstuvwxy'],
  index = 0, children = [], pathname = '/watch', shortsPreviousEnabled = true,
  search = Array.isArray(playlist) && playlist.length ? '?list=TEST_LIST' : '',
  nextButtonEnabled = true,
} = {}) {
  const snapshots = [];
  const events = new Map();
  const windowEvents = new Map();
  const timers = [];
  const commands = [];
  let currentVideoId = videoId;
  let currentIndex = index;
  let now = Date.now();
  const media = {
    paused: !playing, ended: false, isConnected: true,
    currentTime: 40, duration: 300, playbackRate: 1, muted: false, readyState: 4, error: null,
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
    getVideoData() { return { title, author: channel, video_id: currentVideoId }; },
    getDuration() { return media.duration; },
    getCurrentTime() { return media.currentTime; },
    getPlaylist() { return playlist; },
    getPlaylistIndex() { return currentIndex; },
    getPlayerState() { return media.ended ? 0 : (media.paused ? 2 : (media.readyState < 3 ? 3 : 1)); },
    nextVideo() { commands.push('next'); },
    previousVideo() { commands.push('previous'); },
    seekTo(value) { media.currentTime = value; commands.push(['seek', value]); },
    setPlaybackRate(value) { media.playbackRate = value; commands.push(['speed', value]); },
    playVideo() { media.play(); },
    isMuted() { return media.muted; },
    mute() { media.muted = true; commands.push('mute'); },
    unMute() { media.muted = false; commands.push('unmute'); },
  };
  function controlButton(name) {
    return {
      getAttribute() { return null; },
      disabled: (name === 'previous-short' && !shortsPreviousEnabled) ||
        (name === 'next' && !nextButtonEnabled),
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
    location: { hostname: host, pathname, search },
    navigator: { mediaSession },
    WebviewKioskBackgroundMedia: {
      onMediaInfoChanged(id, info) { snapshots.push([id, JSON.parse(info)]); },
      onPlaybackStateChanged() {},
      canAutoResume() { return true; },
    },
    addEventListener(name, fn) { windowEvents.set(name, [...(windowEvents.get(name) ?? []), fn]); },
    setInterval(fn) { timers.push(fn); },
    postMessage(data) {
      for (const fn of windowEvents.get('message') ?? []) fn({ source: window.parent, data });
    },
  };
  window.parent = window;
  for (const child of children) child.window.parent = window;
  const sandbox = {
    window, document, navigator: window.navigator, location: window.location, setTimeout, isFinite,
    Date: class extends Date { static now() { return now; } },
  };
  vm.runInNewContext(documentScript, sandbox);
  vm.runInNewContext(recoveryScript, sandbox);
  vm.runInNewContext(richScript, sandbox);
  return {
    window, snapshots, commands, media, mediaSession, player: root, frameId: window.__wvkFrameId,
    replaceMedia(replacement) {
      media.isConnected = false;
      elements = [replacement];
    },
    tick() { for (const fn of timers) fn(); },
    advance(ms) { now += ms; for (const fn of timers) fn(); },
    setPlaylist(list, at) { playlist = list; currentIndex = at; },
    setVideoId(id) { currentVideoId = id; },
    setNextButtonEnabled(value) { nextButtonEnabled = value; },
    finishTrack({ id = 'noprstuvwxy', at = 1, position = 0, duration = 180, playing = true } = {}) {
      currentVideoId = id;
      currentIndex = at;
      media.currentTime = position;
      media.duration = duration;
      media.error = null;
      media.currentSrc = `https://media.example.test/${id}.mp4`;
      media.src = media.currentSrc;
      media.readyState = 4;
      media.paused = !playing;
      for (const name of ['loadedmetadata', playing ? 'playing' : 'canplay']) {
        for (const fn of events.get(name) ?? []) fn({ target: media });
      }
    },
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
  const restart = frame();
  restart.window.__wvkControlRichMedia('previous', restart.frameId, 0);
  assert.deepEqual(restart.commands, [['seek', 0]], 'Previous restarts when there is progress');
});

test('YouTube playlist previous, seekbar, skip and speed control the existing player', () => {
  const f = frame({ index: 1 });
  f.media.currentTime = 0;
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  f.finishTrack({ id: 'abcdefghijk', at: 0 });
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
  const playingChild = frame({ host: 'www.youtube.com', index: 0 });
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
  f.media.currentTime = 0;
  const info = f.snapshots.at(-1)[1];
  assert.equal(info.canNext, true);
  assert.equal(info.canPrevious, true);
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.deepEqual(f.commands, ['next-click', 'previous-click']);
});

test('mobile YouTube identifies separate next and previous buttons by order', () => {
  const f = frame({ host: 'm.youtube.com', playlist: [] });
  f.media.currentTime = 0;
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
  f.media.currentTime = 0;
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  f.finishTrack();
  f.window.__wvkControlRichMedia('seek', f.frameId, 90_000);
  assert.deepEqual(f.commands, ['next-short-click', 'previous-short-click', ['seek', 90]]);
});

test('Shorts without a previous item can restart progress without invoking playlist methods', () => {
  const f = frame({ pathname: '/shorts/zyxwvutsrqp', shortsPreviousEnabled: false });
  assert.equal(f.snapshots.at(-1)[1].canPrevious, false);
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.deepEqual(f.commands, [['seek', 0]]);
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


test('targeting Play at the parent frame does not resume paused child players', () => {
  const child = frame({ host: 'www.youtube.com' });
  const parent = frame({ host: 'example.org', children: [child] });
  parent.window.__wvkSetHostBackground(true);
  parent.window.__wvkControlMedia('pause');
  parent.window.__wvkControlRichMedia('play', parent.frameId, 0);
  child.tick();
  assert.equal(parent.media.paused, false);
  assert.equal(child.media.paused, true);
});

test('targeting Pause at the parent frame leaves child players alone', () => {
  const child = frame({ host: 'www.youtube.com' });
  const parent = frame({ host: 'example.org', children: [child] });
  parent.window.__wvkControlRichMedia('pause', parent.frameId, 0);
  assert.equal(parent.media.paused, true);
  assert.equal(child.media.paused, false);
});

test('targeting Play at a child reaches that child and leaves its siblings paused', () => {
  const child = frame({ host: 'www.youtube.com' });
  const sibling = frame({ host: 'music.example.test' });
  const parent = frame({ host: 'example.org', playing: false, children: [child, sibling] });
  parent.window.__wvkControlMedia('pause');
  parent.window.__wvkControlRichMedia('play', child.frameId, 0);
  assert.equal(child.media.paused, false);
  assert.equal(sibling.media.paused, true);
  assert.equal(parent.media.paused, true);
});

test('Previous restarts an ordinary seekable item even without a playlist', () => {
  const f = frame({ host: 'music.example.test' });
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.equal(f.media.currentTime, 0);
  assert.equal(f.media.paused, false);
  assert.deepEqual(f.commands, []);
  f.media.currentTime = 0.25;
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.equal(f.media.currentTime, 0, 'even small progress can restart without a previous track');
});

test('Previous restarts after three seconds, then a near-start press selects the previous track', () => {
  const f = frame({ index: 1 });
  f.media.currentTime = 3.001;
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.deepEqual(f.commands, [['seek', 0]]);
  f.media.currentTime = 0.25;
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.deepEqual(f.commands, [['seek', 0], 'previous']);
});

test('Previous uses the current position and the site previous-track handler near the start', () => {
  const f = frame({ host: 'music.example.test' });
  let previous = 0;
  f.mediaSession.setActionHandler('previoustrack', () => { previous++; });
  f.media.currentTime = 5;
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.equal(f.media.currentTime, 0);
  assert.equal(previous, 0);
  // Native metadata still contains an older position; the decision must be local.
  f.media.currentTime = 3;
  f.window.__wvkControlRichMedia('previous', f.frameId, 0);
  assert.equal(previous, 1);
});

test('Previous does not seek live media or start a paused item', () => {
  const live = frame({ host: 'music.example.test' });
  live.media.duration = Infinity;
  live.window.__wvkControlRichMedia('previous', live.frameId, 0);
  assert.equal(live.media.currentTime, 40);
  let previous = 0;
  live.mediaSession.setActionHandler('previoustrack', () => { previous++; });
  live.window.__wvkControlRichMedia('previous', live.frameId, 0);
  assert.equal(previous, 1, 'live playlists can still select the previous item');

  const paused = frame({ host: 'music.example.test', playing: false });
  paused.window.__wvkControlRichMedia('previous', paused.frameId, 0);
  assert.equal(paused.media.currentTime, 0);
  assert.equal(paused.media.paused, true);
  assert.deepEqual(paused.commands, []);
});

test('Mute toggles the selected HTML player without pausing, seeking, or changing volume', () => {
  for (const playing of [true, false]) {
    const f = frame({ host: 'music.example.test', playing });
    f.media.volume = 0.6;
    assert.equal(f.snapshots.at(-1)[1].canMute, true);
    f.window.__wvkControlRichMedia('mute', f.frameId, 0);
    assert.equal(f.media.muted, true);
    assert.equal(f.snapshots.at(-1)[1].muted, true);
    f.window.__wvkControlRichMedia('mute', f.frameId, 0);
    assert.equal(f.media.muted, false);
    assert.equal(f.snapshots.at(-1)[1].muted, false);
    assert.equal(f.media.paused, !playing);
    assert.equal(f.media.currentTime, 40);
    assert.equal(f.media.volume, 0.6);
    assert.deepEqual(f.commands, []);
  }
});

test('site volume changes refresh the notification mute state', () => {
  const f = frame({ host: 'music.example.test' });
  f.media.muted = true;
  f.emit('volumechange');
  assert.equal(f.snapshots.at(-1)[1].muted, true);
  f.media.muted = false;
  f.emit('volumechange');
  assert.equal(f.snapshots.at(-1)[1].muted, false);
});

test('Mute and restart commands affect only the selected cross-origin player', () => {
  const selected = frame();
  const sibling = frame({ host: 'music.example.test' });
  const parent = frame({ host: 'example.test', playing: false, children: [selected, sibling] });
  parent.window.__wvkControlRichMedia('mute', selected.frameId, 0);
  parent.window.__wvkControlRichMedia('previous', selected.frameId, 0);
  assert.equal(selected.media.muted, true);
  assert.equal(selected.media.currentTime, 0);
  assert.equal(sibling.media.muted, false);
  assert.equal(sibling.media.currentTime, 40);
  assert.equal(parent.media.muted, false);
  assert.equal(parent.media.currentTime, 40);
});

test('Mute uses the YouTube API and can fall back to the existing HTML player', () => {
  const f = frame();
  f.window.__wvkControlRichMedia('mute', f.frameId, 0);
  assert.equal(f.media.muted, true);
  f.window.__wvkControlRichMedia('mute', f.frameId, 0);
  assert.equal(f.media.muted, false);
  assert.deepEqual(f.commands, ['mute', 'unmute']);
  f.player.mute = () => { throw new Error('unavailable'); };
  f.window.__wvkControlRichMedia('mute', f.frameId, 0);
  assert.equal(f.media.muted, true);
  assert.equal(f.snapshots.at(-1)[1].muted, true);
});

test('Mute is unavailable when the selected player has no mute capability', () => {
  const f = frame({ host: 'music.example.test' });
  delete f.media.muted;
  f.window.__wvkRefreshRichMedia(true);
  assert.equal(f.snapshots.at(-1)[1].canMute, false);
  f.window.__wvkControlRichMedia('mute', f.frameId, 0);
  assert.equal(f.media.muted, undefined);
  assert.deepEqual(f.commands, []);
});

test('Mute stays consistent when a site API and its HTML player disagree', () => {
  const f = frame();
  f.player.isMuted = () => false;
  f.media.muted = true;
  f.emit('volumechange');
  assert.equal(f.snapshots.at(-1)[1].muted, true, 'the HTML mute flag still silences playback');
  f.window.__wvkControlRichMedia('mute', f.frameId, 0);
  assert.equal(f.media.muted, false);
  assert.equal(f.snapshots.at(-1)[1].muted, false);
  f.player.mute = () => {};
  f.window.__wvkControlRichMedia('mute', f.frameId, 0);
  assert.equal(f.media.muted, true);
  assert.equal(f.snapshots.at(-1)[1].muted, true);
});

test('track navigation reports loading before the website starts changing the player', () => {
  const f = frame();
  f.player.nextVideo = () => {
    const loading = f.snapshots.at(-1)[1];
    assert.equal(loading.buffering, true);
    assert.equal(loading.positionMs, 0);
    assert.equal(loading.durationMs, -1);
    assert.equal(loading.canSeek, false);
    f.commands.push('next');
  };
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  f.media.currentTime = 45;
  f.tick();
  assert.equal(f.snapshots.at(-1)[1].buffering, true);
  assert.equal(f.snapshots.at(-1)[1].positionMs, 0);
});

test('playlist loading ends on actual readiness and uses the new HTML timeline over stale API data', () => {
  const f = frame();
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  f.media.readyState = 1;
  f.emit('loadstart');
  f.emit('waiting');
  f.player.getCurrentTime = () => 45;
  f.player.getDuration = () => 300;
  f.finishTrack({ position: 0.25, duration: 180 });
  const ready = f.snapshots.at(-1)[1];
  assert.equal(ready.buffering, false);
  assert.equal(ready.positionMs, 250);
  assert.equal(ready.durationMs, 180_000);
  assert.equal(ready.canSeek, true);
});

test('same-resource YouTube transitions finish on playing after a buffering state', () => {
  const f = frame();
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  f.player.getPlayerState = () => 3;
  f.tick();
  assert.equal(f.snapshots.at(-1)[1].buffering, true);
  f.setVideoId('noprstuvwxy');
  f.setPlaylist(['abcdefghijk', 'noprstuvwxy'], 1);
  f.media.currentTime = 0.1;
  f.player.getPlayerState = () => 1;
  f.emit('playing');
  assert.equal(f.snapshots.at(-1)[1].buffering, false);
  assert.equal(f.snapshots.at(-1)[1].positionMs, 100);
});

test('ordinary buffering preserves position and stops once data is ready', () => {
  const f = frame({ host: 'music.example.test' });
  f.media.readyState = 2;
  f.emit('waiting');
  assert.equal(f.snapshots.at(-1)[1].buffering, true);
  assert.equal(f.snapshots.at(-1)[1].positionMs, 40_000);
  assert.equal(f.snapshots.at(-1)[1].durationMs, 300_000);
  f.media.readyState = 4;
  f.emit('playing');
  assert.equal(f.snapshots.at(-1)[1].buffering, false);
  f.emit('stalled');
  f.tick();
  assert.equal(f.snapshots.at(-1)[1].buffering, false, 'a fetch stall with buffered data is not a playback stall');
});

test('notification Pause cancels the loading indicator even if the API still reports buffering', () => {
  const f = frame();
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  f.player.getPlayerState = () => 3;
  f.window.__wvkControlMedia('pause');
  f.emit('waiting');
  f.window.__wvkRefreshRichMedia(true);
  assert.equal(f.snapshots.at(-1)[1].buffering, false);
  assert.equal(f.media.paused, true);
});

test('track errors and ignored or throwing commands cannot leave a phantom spinner', () => {
  const failed = frame();
  failed.window.__wvkControlRichMedia('next', failed.frameId, 0);
  failed.media.error = { code: 2 };
  failed.emit('error');
  assert.equal(failed.snapshots.at(-1)[1].buffering, false);
  assert.equal(failed.snapshots.at(-1)[1].canSeek, false);

  const ignored = frame();
  ignored.window.__wvkControlRichMedia('next', ignored.frameId, 0);
  ignored.advance(10_001);
  assert.equal(ignored.snapshots.at(-1)[1].buffering, false);
  assert.equal(ignored.snapshots.at(-1)[1].positionMs, 40_000);

  const throwing = frame();
  throwing.player.nextVideo = () => { throw new Error('unavailable'); };
  throwing.window.__wvkControlRichMedia('next', throwing.frameId, 0);
  assert.equal(throwing.snapshots.at(-1)[1].buffering, false);
});

test('cued and paused replacement tracks stop loading without starting playback', () => {
  const f = frame();
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  f.player.getPlayerState = () => 5;
  f.finishTrack({ playing: false });
  assert.equal(f.snapshots.at(-1)[1].buffering, false);
  assert.equal(f.snapshots.at(-1)[1].positionMs, 0);
  assert.equal(f.media.paused, true);
  assert.deepEqual(f.commands, ['next']);
});

test('muting cannot erase known playlist navigation when YouTube temporarily clears its getters', () => {
  for (const unavailable of [null, []]) {
    const f = frame({ nextButtonEnabled: false });
    assert.equal(f.snapshots.at(-1)[1].canNext, true);
    f.player.mute = () => {
      f.media.muted = true;
      f.setPlaylist(unavailable, -1);
      f.emit('volumechange');
    };
    f.window.__wvkControlRichMedia('mute', f.frameId, 0);
    assert.equal(f.snapshots.at(-1)[1].muted, true);
    assert.equal(f.snapshots.at(-1)[1].canNext, true);
    f.tick();
    assert.equal(f.snapshots.at(-1)[1].canNext, true);
    f.window.__wvkControlRichMedia('next', f.frameId, 0);
    assert.deepEqual(f.commands, ['next'], 'cached navigation must remain usable, not just look enabled');
  }
});

test('cached navigation respects new playlist bounds, item identity, and leaving the playlist', () => {
  const last = frame({ nextButtonEnabled: false });
  last.finishTrack();
  assert.equal(last.snapshots.at(-1)[1].canNext, false);
  last.window.__wvkControlRichMedia('mute', last.frameId, 0);
  assert.equal(last.snapshots.at(-1)[1].canNext, false);

  const changed = frame({ nextButtonEnabled: false });
  changed.setPlaylist(null, -1);
  changed.setVideoId('newnewnew12');
  changed.tick();
  assert.equal(changed.snapshots.at(-1)[1].canNext, false);

  const removed = frame({ nextButtonEnabled: false });
  removed.setPlaylist([], -1);
  removed.window.location.search = '';
  removed.tick();
  assert.equal(removed.snapshots.at(-1)[1].canNext, false);
});

test('loading metadata remains scoped to the selected cross-origin player', () => {
  const selected = frame();
  const sibling = frame();
  const parent = frame({ host: 'example.test', children: [selected, sibling] });
  parent.window.__wvkControlRichMedia('next', selected.frameId, 0);
  assert.equal(selected.snapshots.at(-1)[1].buffering, true);
  assert.equal(sibling.snapshots.at(-1)[1].buffering, false);
  assert.equal(parent.snapshots.at(-1)[1].buffering, false);
});

test('volume-only refreshes preserve UI-managed YouTube playlist buttons during their rerender', () => {
  const f = frame({ playlist: [], search: '?list=UI_MANAGED' });
  assert.equal(f.snapshots.at(-1)[1].canNext, true);
  const start = f.snapshots.length;
  f.player.mute = () => {
    f.media.muted = true;
    f.setNextButtonEnabled(false);
    f.emit('volumechange');
    f.mediaSession.setActionHandler('nexttrack', null);
    f.mediaSession.setActionHandler('seekto', null);
  };
  f.window.__wvkControlRichMedia('mute', f.frameId, 0);
  assert.equal(f.snapshots.at(-1)[1].muted, true);
  assert.ok(f.snapshots.slice(start).every(([, info]) => info.canNext), 'no disabled Next snapshot may leak during mute');
  f.setNextButtonEnabled(true);
  f.tick();
  assert.equal(f.snapshots.at(-1)[1].canNext, true);
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  assert.deepEqual(f.commands, ['next-click']);
});

test('volume-only refreshes still respect real playlist changes and different media', () => {
  const f = frame({ nextButtonEnabled: false });
  assert.equal(f.snapshots.at(-1)[1].canNext, true);
  f.setPlaylist(['abcdefghijk'], 0);
  f.media.muted = true;
  f.emit('volumechange');
  assert.equal(f.snapshots.at(-1)[1].canNext, false, 'fresh playlist bounds take precedence over old capabilities');

  const replaced = frame({ playlist: [], search: '?list=UI_MANAGED' });
  replaced.setNextButtonEnabled(false);
  replaced.setVideoId('newnewnew12');
  replaced.emit('volumechange');
  assert.equal(replaced.snapshots.at(-1)[1].canNext, false);
});

test('aborting the old resource during a track change cannot restart its clock', () => {
  const f = frame();
  f.window.__wvkControlRichMedia('next', f.frameId, 0);
  f.emit('abort');
  assert.equal(f.snapshots.at(-1)[1].buffering, true);
  assert.equal(f.snapshots.at(-1)[1].positionMs, 0);
  assert.equal(f.snapshots.at(-1)[1].durationMs, -1);
  f.finishTrack();
  assert.equal(f.snapshots.at(-1)[1].buffering, false);
});
