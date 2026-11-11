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
      if (window.__wvkRichMediaInstalled || !window.__wvkFrameId) return;
      window.__wvkRichMediaInstalled = true;
      var frameId = window.__wvkFrameId;
      var youtube = /(^|\.)youtube(-nocookie)?\.com$/.test(location.hostname.toLowerCase());
      var handlers = Object.create(null);
      var lastSnapshot = '';
      var lastProgressReport = 0;
      var lastPlayedMedia = null;
      var lastMediaActive = false;

      function player() { return document.getElementById('movie_player'); }
      function media() {
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
        var button = document.querySelector('.ytp-' + (type === 'next' ? 'next' : 'prev') + '-button') ||
          document.querySelector('ytmusic-player-bar .' + type + '-button') ||
          document.querySelector('ytmusic-player-controls .' + type + '-button');
        if (button) return button;
        // Mobile YouTube reuses one class for both arrows: previous is first.
        var buttons = document.querySelectorAll('.player-controls-middle .player-middle-controls-prev-next-button');
        if (buttons.length > 1) return buttons[type === 'next' ? buttons.length - 1 : 0];
        return null;
      }
      function snapshot() {
        var element = media();
        var root = youtube && player();
        if (!element && !root) return null;
        var data = call(root, 'getVideoData', null) || {};
        var sessionMeta = (navigator.mediaSession && navigator.mediaSession.metadata) || {};
        var title = (youtube && data.title) || sessionMeta.title ||
          meta('og:title') || document.title || '';
        var artist = (youtube && (data.author || data.channelName)) ||
          sessionMeta.artist || meta('og:site_name') || '';
        var art = '';
        var videoId = youtube && (data.video_id || data.videoId);
        if (videoId && /^[a-zA-Z0-9_-]{11}$/.test(videoId)) {
          art = 'https://i.ytimg.com/vi/' + videoId + '/hqdefault.jpg';
        } else if (sessionMeta.artwork && sessionMeta.artwork.length) {
          art = sessionMeta.artwork[sessionMeta.artwork.length - 1].src || '';
        } else {
          art = meta('og:image');
        }
        var duration = nonnegative(call(root, 'getDuration', element ? element.duration : -1));
        if (duration < 0 && element) duration = nonnegative(element.duration);
        var position = nonnegative(call(root, 'getCurrentTime', element ? element.currentTime : -1));
        if (position < 0 && element) position = nonnegative(element.currentTime);
        var playlist = call(root, 'getPlaylist', null);
        var index = call(root, 'getPlaylistIndex', -1);
        var hasList = playlist && playlist.length > 1 && index >= 0;
        var previous = !!handlers.previoustrack ||
          !!(youtube && ((hasList && index > 0 && typeof root.previousVideo === 'function') ||
            enabledButton(transportButton('previous'))));
        var next = !!handlers.nexttrack ||
          !!(youtube && ((hasList && index < playlist.length - 1 && typeof root.nextVideo === 'function') ||
            enabledButton(transportButton('next'))));
        var live = data.isLive === true ||
          !!(element && element.duration === Infinity) ||
          !!(root && root.classList && root.classList.contains('ytp-live'));
        var seek = !live && duration > 0 && !!(element || (root && typeof root.seekTo === 'function') || handlers.seekto);
        var speed = element && element.playbackRate > 0 ? element.playbackRate : 1;
        return {
          title: String(title), artist: String(artist), artworkUrl: String(art),
          durationMs: duration >= 0 ? Math.round(duration * 1000) : -1,
          positionMs: position >= 0 ? Math.round(position * 1000) : -1,
          canPrevious: previous, canNext: next, canSeek: seek,
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
              report(true);
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
        if (action === 'pause') {
          window.__wvkControlMedia('pause');
        } else if (action === 'play') {
          window.__wvkControlMedia('resume');
          // The page may have paused itself on Home, rather than our pause list.
          // Never start an unrelated, never-played video from a notification.
          if (lastMediaActive && element && element.paused) {
            if (root && typeof root.playVideo === 'function') root.playVideo();
            else if (typeof element.play === 'function') {
              var pending = element.play();
              if (pending && pending.catch) pending.catch(function () {});
            }
          }
        } else if (action === 'next' && data && data.canNext) {
          if (handlers.nexttrack && !youtube) handlers.nexttrack({action: 'nexttrack'});
          else if (root && typeof root.nextVideo === 'function' &&
                   call(root, 'getPlaylist', null) && call(root, 'getPlaylist', null).length > 1) {
            root.nextVideo();
          } else {
            var next = transportButton('next');
            if (enabledButton(next)) next.click();
          }
        } else if (action === 'previous' && data && data.canPrevious) {
          if (handlers.previoustrack && !youtube) handlers.previoustrack({action: 'previoustrack'});
          else if (root && typeof root.previousVideo === 'function' &&
                   call(root, 'getPlaylist', null) && call(root, 'getPlaylist', null).length > 1) {
            root.previousVideo();
          } else {
            var previous = transportButton('previous');
            if (enabledButton(previous)) previous.click();
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
      ['play','playing','pause','ended','loadedmetadata','durationchange','ratechange'].forEach(function (name) {
        document.addEventListener(name, function (event) {
          if (name === 'play' || name === 'playing') {
            lastPlayedMedia = event.target;
            lastMediaActive = true;
          }
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
      window.setInterval(function () {
        var element = media();
        if (element && !element.paused && !element.ended) {
          lastPlayedMedia = element;
          lastMediaActive = true;
        }
        report(false);
      }, 2000);
      var playing = media();
      if (playing && !playing.paused && !playing.ended) {
        lastPlayedMedia = playing;
        lastMediaActive = true;
      }
      report(true);
    })();
    """
