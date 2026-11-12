package uk.nktnet.webviewkiosk.utils.webview

/**
 * Read metadata from the *existing* HTML/YouTube player, without extracting a
 * stream. Each document (including cross-origin iframes) reports only its own media,
 * and transport commands carry the active document's frame ID to avoid advancing
 * ads.
 */
internal const val RICH_MEDIA_SCRIPT =
    """
    (function () {
      if (window.__wvkRichMediaInstalled || !window.__wvkFrameId || !window.__wvkMediaIdentity) return;
      window.__wvkRichMediaInstalled = true;
      var frameId = window.__wvkFrameId;
      var youtube = /(^|\.)youtube(-nocookie)?\.com$/.test(location.hostname.toLowerCase());
      var handlers = Object.create(null);
      var lastSnapshot = '';
      var lastProgressReport = 0;
      var lastPlayedMedia = null;
      var lastPlayback = null;
      var mediaIdentity = window.__wvkMediaIdentity;
      var playlistCache = null;
      var currentNavigation = null;
      var navigationState = null;
      var changingVolume = false;
      var pendingTrack = null;
      var waitingMedia = new WeakSet();
      var explicitlyPaused = false;

      window.__wvkRichMediaExplicitAction = function (action) {
        if (action === 'pause' || action === 'stop') {
          explicitlyPaused = true;
          pendingTrack = null;
          waitingMedia = new WeakSet();
        } else if (action === 'resume') explicitlyPaused = false;
      };

      function rememberPlaying(media) {
        lastPlayedMedia = media;
        lastPlayback = mediaIdentity.capture(media);
      }

      function isShorts() {
        return youtube && /^\/shorts(?:\/|$)/.test(location.pathname || '');
      }
      function shortsVideo() {
        if (!isShorts()) return null;
        var active = document.querySelector('ytd-reel-video-renderer[is-active] video') ||
          document.querySelector('#shorts-player video');
        if (active) return active;
        // Mobile YouTube uses shorts-video and may preload neighbouring clips.
        var candidates = document.querySelectorAll('shorts-video video');
        for (var i = 0; i < candidates.length; i++) {
          if (!candidates[i].paused && !candidates[i].ended) return candidates[i];
        }
        return candidates[0] || null;
      }
      function player() {
        if (isShorts()) {
          var active = shortsVideo();
          if (active && typeof active.closest === 'function') {
            var root = active.closest('#movie_player, #shorts-player, .html5-video-player');
            if (root) return root;
          }
          return document.querySelector('ytd-reel-video-renderer[is-active] #movie_player') ||
            document.querySelector('#shorts-player') || document.getElementById('movie_player');
        }
        return document.getElementById('movie_player');
      }
      function media() {
        var activeShort = shortsVideo();
        if (activeShort) return activeShort;
        var root = youtube && player();
        if (root && root.querySelector) {
          var video = root.querySelector('video');
          if (video) return video;
        }
        var elements = document.querySelectorAll('audio,video');
        for (var i = 0; i < elements.length; ++i) {
          if (!elements[i].paused && !elements[i].ended) return elements[i];
        }
        return lastPlayedMedia && lastPlayedMedia.isConnected ? lastPlayedMedia : (elements[0] || null);
      }
      function call(root, name, fallback) {
        try {
          if (root && typeof root[name] === 'function') return root[name]();
        } catch (ignored) {}
        return fallback;
      }
      function nonnegative(value) {
        return typeof value === 'number' && isFinite(value) && value >= 0 ? value : -1;
      }
      function meta(name) {
        var node = document.querySelector('meta[property="' + name + '"]') ||
          document.querySelector('meta[name="' + name + '"]');
        return node && node.content ? node.content : '';
      }
      function enabledButton(button) {
        return !!button && button.getAttribute('aria-disabled') !== 'true' &&
          !button.disabled && !button.hasAttribute('disabled');
      }
      function transportButton(action) {
        var type = action === 'next' ? 'next' : 'previous';
        if (isShorts()) {
          var id = type === 'next' ? 'navigation-button-down' : 'navigation-button-up';
          return document.querySelector('#' + id + ' button') ||
            document.querySelector('#' + id);
        }
        var button = document.querySelector('.ytp-' + (type === 'next' ? 'next' : 'prev') + '-button') ||
          document.querySelector('ytmusic-player-bar .' + type + '-button') ||
          document.querySelector('ytmusic-player-controls .' + type + '-button');
        if (button) return button;
        // Mobile YouTube reuses one class for both arrows: previous is first.
        var buttons = document.querySelectorAll('.player-controls-middle .player-middle-controls-prev-next-button');
        if (buttons.length > 1) return buttons[type === 'next' ? buttons.length - 1 : 0];
        return null;
      }
      function shortsText(selector) {
        var active = isShorts() && document.querySelector('ytd-reel-video-renderer[is-active]');
        var node = active && active.querySelector(selector);
        return node && node.textContent ? node.textContent.trim() : '';
      }
      function videoIdentity(root) {
        var shortsId = isShorts() && /^\/shorts\/([a-zA-Z0-9_-]{11})(?:\/|$)/.exec(location.pathname || '');
        var data = call(root, 'getVideoData', null) || {};
        return (shortsId && shortsId[1]) || data.video_id || data.videoId || '';
      }
      function playlistNavigation(root, element, videoId) {
        var context = /(?:\?|&)list=([^&]*)/.exec(location.search || '');
        context = context ? context[1] : '';
        var list = call(root, 'getPlaylist', null);
        var index = call(root, 'getPlaylistIndex', -1);
        if (Array.isArray(list) && list.length > 0 && typeof index === 'number' &&
            Math.floor(index) === index && index >= 0 && index < list.length) {
          playlistCache = {
            root: root, context: context, videoId: videoId || list[index],
            identity: mediaIdentity.capture(element), index: index, length: list.length
          };
          return playlistCache;
        }
        // Volume changes can temporarily remove YouTube's playlist getters and
        // buttons. Unknown data must not erase bounds known for this same item.
        var sameItem = playlistCache && playlistCache.context === context &&
          (videoId ? playlistCache.videoId === videoId :
            playlistCache.root === root && mediaIdentity.matches(playlistCache.identity, element));
        if (sameItem && !(Array.isArray(list) && list.length === 0 && !context)) return playlistCache;
        playlistCache = null;
        return null;
      }
      function stableNavigation(root, element, videoId, navigation, muted, previous, next) {
        var cached = navigationState;
        var context = (location.pathname || '') + (location.search || '');
        var sameItem = cached && cached.context === context &&
          (videoId ? cached.videoId === videoId : mediaIdentity.matches(cached.identity, element));
        var sameBounds = cached &&
          ((!navigation && !cached.navigation) ||
            (navigation && cached.navigation && navigation.index === cached.navigation.index &&
              navigation.length === cached.navigation.length));
        var holding = sameItem && sameBounds &&
          (changingVolume || muted || cached.muted || cached.holding);
        var observedPrevious = previous;
        var observedNext = next;
        if (holding) {
          // Muting can hide/reset YouTube's controls for longer than one event.
          // Preserve known operations across every snapshot, including commands,
          // only while the current player still provides a way to execute them.
          if (cached.previous && root && typeof root.previousVideo === 'function' &&
              (!navigation || navigation.index > 0)) previous = true;
          if (cached.next && root && typeof root.nextVideo === 'function' &&
              (!navigation || navigation.index < navigation.length - 1)) next = true;
        }
        navigationState = {
          context: context, videoId: videoId || (sameItem ? cached.videoId : ''),
          identity: mediaIdentity.capture(element), navigation: navigation,
          previous: !!previous, next: !!next, muted: muted,
          // Unmuting can also rerender the controls. Release the retained state
          // once they recover, or immediately on a new item/context/bounds.
          holding: !!holding &&
            (changingVolume || muted || previous !== observedPrevious || next !== observedNext)
        };
        return navigationState;
      }
      function loadingState(root, element) {
        if (explicitlyPaused) return false;
        if (element && element.error) {
          pendingTrack = null;
          waitingMedia.delete(element);
          return false;
        }
        var state = call(root, 'getPlayerState', null);
        var ready = element && typeof element.readyState === 'number' ? element.readyState : 4;
        var cued = state === 5 && ready >= 1;
        var active = element && !element.paused && !element.ended;
        var loading = !cued && (state === 3 ||
          (element && waitingMedia.has(element) && (active || !!pendingTrack)) ||
          (active && ready < 3));
        if (pendingTrack) {
          if (loading || state === -1) pendingTrack.sawLoading = true;
          var changed = element && pendingTrack.identity &&
            !mediaIdentity.matches(pendingTrack.identity, element);
          var pausedReady = element && element.paused && ready >= 1 && state !== 3 && state !== -1;
          var playable = (!loading && ready >= 3 && state !== -1) || pausedReady || cued;
          if (playable && (changed || pendingTrack.readyEvent ||
              (cued && videoIdentity(root) !== pendingTrack.videoId))) {
            pendingTrack = null;
            if (pausedReady || cued) {
              waitingMedia.delete(element);
              loading = false;
            }
          } else if (Date.now() - pendingTrack.requestedAt > 10000 && !loading) {
            // A site may ignore a track command. Do not leave a phantom spinner.
            pendingTrack = null;
          }
        }
        return loading || pendingTrack !== null;
      }
      function changeTrack(root, element, action) {
        pendingTrack = {
          identity: mediaIdentity.capture(element), videoId: videoIdentity(root),
          requestedAt: Date.now(), sawLoading: false, readyEvent: false
        };
        report(true);
        try { action(); }
        catch (ignored) { pendingTrack = null; }
      }
      function snapshot() {
        var element = media();
        var root = youtube && player();
        if (!element && !root) return null;
        var data = call(root, 'getVideoData', null) || {};
        var sessionMeta = (navigator.mediaSession && navigator.mediaSession.metadata) || {};
        var title = (youtube && data.title) ||
          shortsText('yt-shorts-video-title-view-model h2, #video-title') ||
          sessionMeta.title || meta('og:title') || document.title || '';
        var artist = (youtube && (data.author || data.channelName)) ||
          shortsText('#channel-name, yt-reel-channel-bar-view-model a') ||
          sessionMeta.artist || meta('og:site_name') || '';
        var art = '';
        var shortsId = isShorts() && /^\/shorts\/([a-zA-Z0-9_-]{11})(?:\/|$)/.exec(location.pathname || '');
        var videoId = (shortsId && shortsId[1]) || (youtube && (data.video_id || data.videoId));
        if (videoId && /^[a-zA-Z0-9_-]{11}$/.test(videoId)) {
          art = 'https://i.ytimg.com/vi/' + videoId + '/hqdefault.jpg';
        } else if (sessionMeta.artwork && sessionMeta.artwork.length) {
          art = sessionMeta.artwork[sessionMeta.artwork.length - 1].src || '';
        } else {
          art = meta('og:image');
        }
        // The API may still describe the old clip during a playlist transition.
        // Prefer the selected HTML player's actual timeline whenever available.
        var duration = nonnegative(element ? element.duration : -1);
        if (duration < 0) duration = nonnegative(call(root, 'getDuration', -1));
        var position = nonnegative(element ? element.currentTime : -1);
        if (position < 0) position = nonnegative(call(root, 'getCurrentTime', -1));
        var muted = !!(element && element.muted === true) || call(root, 'isMuted', false) === true;
        var navigation = youtube && !isShorts() ? playlistNavigation(root, element, videoId) : null;
        currentNavigation = navigation;
        var previous = !!(!youtube && handlers.previoustrack) ||
          !!(youtube && ((isShorts() && enabledButton(transportButton('previous'))) ||
            (!isShorts() && ((navigation && navigation.index > 0 && typeof root.previousVideo === 'function') ||
              enabledButton(transportButton('previous'))))));
        var next = !!(!youtube && handlers.nexttrack) ||
          !!(youtube && ((isShorts() && enabledButton(transportButton('next'))) ||
            (!isShorts() && ((navigation && navigation.index < navigation.length - 1 && typeof root.nextVideo === 'function') ||
              enabledButton(transportButton('next'))))));
        if (youtube && !isShorts()) {
          var stable = stableNavigation(root, element, videoId, navigation, muted, previous, next);
          previous = stable.previous;
          next = stable.next;
        }
        var live = data.isLive === true ||
          !!(element && element.duration === Infinity) ||
          !!(root && root.classList && root.classList.contains('ytp-live'));
        var seek = !live && !(element && element.error) && duration > 0 &&
          !!(element || (root && typeof root.seekTo === 'function') || handlers.seekto);
        var speed = element && element.playbackRate > 0 ? element.playbackRate : 1;
        var buffering = loadingState(root, element);
        var changingTrack = pendingTrack !== null;
        return {
          title: String(title), artist: String(artist), artworkUrl: String(art),
          durationMs: !changingTrack && duration >= 0 ? Math.round(duration * 1000) : -1,
          positionMs: changingTrack ? 0 : (position >= 0 ? Math.round(position * 1000) : -1),
          buffering: buffering,
          canPrevious: previous, canNext: next, canSeek: seek && !changingTrack,
          canMute: !!(element && typeof element.muted === 'boolean') ||
            !!(root && typeof root.isMuted === 'function' &&
              typeof root.mute === 'function' && typeof root.unMute === 'function'),
          muted: muted,
          canChangeSpeed: !!(element && typeof element.playbackRate === 'number') ||
            !!(root && typeof root.setPlaybackRate === 'function'),
          speed: speed
        };
      }
      function report(force) {
        var data = snapshot();
        if (!data) return;
        var serialized = JSON.stringify(data);
        if (serialized === lastSnapshot && !force) return;
        lastSnapshot = serialized;
        try {
          window.WebviewKioskBackgroundMedia.onMediaInfoChanged(frameId, serialized);
        } catch (ignored) {}
      }
      window.__wvkRefreshRichMedia = report;

      // Sites can advertise playlist operations using the standard Media Session API.
      // Intercept only capabilities; invoke the site's original handler on commands.
      try {
        var session = navigator.mediaSession;
        if (session && typeof session.setActionHandler === 'function') {
          var original = session.setActionHandler;
          session.setActionHandler = function (action, handler) {
            var result = original.apply(this, arguments);
            if (['nexttrack', 'previoustrack', 'seekto', 'seekbackward', 'seekforward'].indexOf(action) !== -1) {
              handlers[action] = typeof handler === 'function' ? handler : null;
              // YouTube resets these handlers during volume changes; its track
              // capabilities come from the player and buttons, not this registry.
              if (!youtube) report(true);
            }
            return result;
          };
        }
      } catch (ignored) {}

      function seekTo(seconds) {
        var data = snapshot();
        if (!data || !data.canSeek || !isFinite(seconds)) return;
        var end = data.durationMs / 1000;
        var at = Math.max(0, Math.min(end, seconds));
        var root = youtube && player();
        if (root && typeof root.seekTo === 'function') root.seekTo(at, true);
        else if (handlers.seekto) handlers.seekto({seekTime: at, fastSeek: false});
        else {
          var element = media();
          if (element) element.currentTime = at;
        }
        report(true);
      }
      function control(action, value) {
        var root = youtube && player();
        var element = media();
        var data = snapshot();
        var navigation = currentNavigation;
        if (action === 'pause') {
          window.__wvkControlMedia('pause', true);
        } else if (action === 'play') {
          window.__wvkControlMedia('resume', true);
          // The page may have paused itself on Home, rather than our pause list.
          // Never start an unrelated, never-played video from a notification.
          if (mediaIdentity.matches(lastPlayback, element) && element.paused && !element.ended) {
            if (root && typeof root.playVideo === 'function') root.playVideo();
            else if (typeof element.play === 'function') {
              var pending = element.play();
              if (pending && pending.catch) pending.catch(function () {});
            }
          }
        } else if (action === 'next' && data && data.canNext) {
          if (isShorts()) {
            var nextShort = transportButton('next');
            if (enabledButton(nextShort)) changeTrack(root, element, function () { nextShort.click(); });
          } else if (handlers.nexttrack && !youtube) {
            changeTrack(root, element, function () { handlers.nexttrack({action: 'nexttrack'}); });
          }
          else if (root && typeof root.nextVideo === 'function' &&
                   navigation && navigation.index < navigation.length - 1) {
            changeTrack(root, element, function () { root.nextVideo(); });
          } else {
            var next = transportButton('next');
            if (enabledButton(next)) changeTrack(root, element, function () { next.click(); });
            else if (root && typeof root.nextVideo === 'function') {
              changeTrack(root, element, function () { root.nextVideo(); });
            }
          }
        } else if (action === 'previous' && data) {
          // Use the current player position, not a potentially stale native tick.
          // Near the start, a second press can still select the previous track.
          if (data.canSeek && data.positionMs > 0 &&
              (data.positionMs > 3000 || !data.canPrevious)) {
            seekTo(0);
          } else if (!data.canPrevious) {
            return;
          } else if (isShorts()) {
            var previousShort = transportButton('previous');
            if (enabledButton(previousShort)) changeTrack(root, element, function () { previousShort.click(); });
          } else if (handlers.previoustrack && !youtube) {
            changeTrack(root, element, function () { handlers.previoustrack({action: 'previoustrack'}); });
          }
          else if (root && typeof root.previousVideo === 'function' &&
                   navigation && navigation.index > 0) {
            changeTrack(root, element, function () { root.previousVideo(); });
          } else {
            var previous = transportButton('previous');
            if (enabledButton(previous)) changeTrack(root, element, function () { previous.click(); });
            else if (root && typeof root.previousVideo === 'function') {
              changeTrack(root, element, function () { root.previousVideo(); });
            }
          }
        } else if (action === 'mute' && data && data.canMute) {
          var muted = !data.muted;
          // Site callbacks can run synchronously before the mute flag changes.
          changingVolume = true;
          try {
            if (root && typeof root.isMuted === 'function' &&
                typeof root.mute === 'function' && typeof root.unMute === 'function') {
              try {
                if (muted) root.mute(); else root.unMute();
              } catch (ignored) {}
            }
            // Sites may mute either the HTML element or their own player API.
            if (element && typeof element.muted === 'boolean') element.muted = muted;
          } finally {
            changingVolume = false;
          }
        } else if (action === 'seek' && typeof value === 'number') {
          seekTo(value / 1000);
        } else if (action === 'rewind' || action === 'forward') {
          var current = data && data.positionMs >= 0 ? data.positionMs / 1000 : -1;
          if (current >= 0) seekTo(current + (action === 'rewind' ? -10 : 30));
        } else if (action === 'speed' && data && data.canChangeSpeed) {
          var rates = [1, 1.25, 1.5, 2];
          var nextRate = rates[0];
          for (var i = 0; i < rates.length; i++) {
            if (rates[i] > data.speed + 0.01) { nextRate = rates[i]; break; }
          }
          if (root && typeof root.setPlaybackRate === 'function') root.setPlaybackRate(nextRate);
          else if (element) element.playbackRate = nextRate;
        }
        report(true);
      }
      function dispatch(action, target, value) {
        if (frameId === target) {
          control(action, value);
          return;
        }
        document.querySelectorAll('iframe').forEach(function (child) {
          try {
            child.contentWindow.postMessage({
              __wvkRichMediaCommand: action, target: target, value: value
            }, '*');
          } catch (ignored) {}
        });
      }
      window.__wvkControlRichMedia = dispatch;
      window.addEventListener('message', function (event) {
        if (window.parent !== window && event.source === window.parent && event.data &&
            typeof event.data.__wvkRichMediaCommand === 'string') {
          dispatch(event.data.__wvkRichMediaCommand, event.data.target, event.data.value);
        }
      });
      ['waiting','loadstart','emptied'].forEach(function (name) {
        document.addEventListener(name, function (event) {
          waitingMedia.add(event.target);
          report(true);
        }, true);
      });
      ['playing','canplay','seeked'].forEach(function (name) {
        document.addEventListener(name, function (event) {
          waitingMedia.delete(event.target);
          if (pendingTrack && event.target === media() &&
              (pendingTrack.sawLoading || videoIdentity(youtube && player()) !== pendingTrack.videoId ||
                !mediaIdentity.matches(pendingTrack.identity, event.target))) pendingTrack.readyEvent = true;
          report(true);
        }, true);
      });
      document.addEventListener('error', function (event) {
        waitingMedia.delete(event.target);
        if (event.target === media()) pendingTrack = null;
        report(true);
      }, true);
      // Aborting the old resource is normal during a track change, not a failure.
      ['play','playing','pause','ended','loadedmetadata','durationchange','ratechange','volumechange','abort'].forEach(function (name) {
        document.addEventListener(name, function (event) {
          if (name === 'play' || name === 'playing') {
            explicitlyPaused = false;
            rememberPlaying(event.target);
          }
          if (name === 'pause' || name === 'ended') waitingMedia.delete(event.target);
          report(true);
        }, true);
      });
      document.addEventListener('timeupdate', function () {
        if (Date.now() - lastProgressReport > 1600) {
          lastProgressReport = Date.now();
          report(false);
        }
      }, true);
      document.addEventListener('DOMContentLoaded', function () { report(true); });
      document.addEventListener('yt-navigate-finish', function () { report(true); });
      window.setInterval(function () {
        var element = media();
        if (element && !element.paused && !element.ended) {
          rememberPlaying(element);
        }
        report(false);
      }, 2000);
      var playing = media();
      if (playing && !playing.paused && !playing.ended) {
        rememberPlaying(playing);
      }
      report(true);
    })();
    """
