package uk.nktnet.webviewkiosk.utils.webview

import android.util.Log
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.lang.ref.WeakReference
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.services.BackgroundMediaPlaybackService

/**
 * A separate playing/paused signal is emitted by each document (including iframes).
 */
internal class PlayingMediaFrames {
    private val frames = mutableSetOf<String>()

    val isPlaying: Boolean
        get() = frames.isNotEmpty()

    fun update(frameId: String, playing: Boolean): Boolean {
        if (frameId.isBlank() || frameId.length > 128) {
            return isPlaying
        }
        if (playing) {
            frames.add(frameId)
        } else {
            frames.remove(frameId)
        }
        return isPlaying
    }

    fun clear() = frames.clear()
}

/**
 * Keeps the existing WebView as the media player. The foreground service only
 * supplies process priority and a user-visible way to stop background playback; it
 * cannot recreate a website's player if Android destroys its Activity.
 */
internal class BackgroundMediaPlaybackController(
    private val webView: WebView,
    private val isActive: () -> Boolean,
) {
    companion object {
        private const val BRIDGE_NAME = "WebviewKioskBackgroundMedia"
        private const val TAG = "BackgroundMediaPlayback"
        private var activeController:
            WeakReference<BackgroundMediaPlaybackController>? =
            null

        fun isPlaybackRequested(): Boolean =
            activeController?.get()?.serviceRequested == true

        fun stopFromNotification() {
            activeController?.get()?.stopPlayback(userInitiated = true)
        }

        fun pauseFromNotification() {
            activeController?.get()?.pauseFromNotificationInternal()
        }

        fun resumeFromNotification() {
            activeController?.get()?.resumeFromNotificationInternal()
        }

        fun isPlaying(): Boolean =
            activeController?.get()?.let {
                it.serviceRequested &&
                    it.frames.isPlaying &&
                    !it.pausedByNotification
            } == true
    }

    private val context = webView.context.applicationContext
    private val frames = PlayingMediaFrames()
    private var disposed = false
    private var serviceRequested = false
    private var stoppedByUser = false
    private var pausedByNotification = false
    private var awaitingResume = false
    private val bridge = MediaStateBridge(webView, ::onPlaybackStateChanged)

    init {
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        withOptionalWebViewFeature(WebViewFeature.DOCUMENT_START_SCRIPT) {
            if (
                WebViewFeature.isFeatureSupported(
                    WebViewFeature.DOCUMENT_START_SCRIPT
                )
            ) {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    DOCUMENT_START_SCRIPT,
                    setOf("*"),
                )
            }
        }
    }

    fun onNavigationStarted() {
        if (!disposed) {
            stoppedByUser = false
            pausedByNotification = false
            awaitingResume = false
            frames.clear()
            stopService()
        }
    }

    fun onPageFinished() {
        if (!disposed) {
            // Older Android WebView providers do not support document-start
            // scripts. This fallback covers media in the main document.
            webView.evaluateJavascript(DOCUMENT_START_SCRIPT, null)
        }
    }

    fun onActivityStarted() {
        if (!disposed) {
            stoppedByUser = false
            webView.evaluateJavascript(
                "window.__wvkReportMediaPlayback && window.__wvkReportMediaPlayback();",
                null,
            )
        }
    }

    private fun onPlaybackStateChanged(frameId: String, playing: Boolean) {
        if (disposed || !isActive()) {
            return
        }
        val shouldPlay = frames.update(frameId, playing)
        if (shouldPlay && (stoppedByUser || pausedByNotification)) {
            // A page may immediately try to restart media after the user
            // paused it from the notification; honour the explicit action.
            pauseMedia(retainResumePosition = pausedByNotification)
        } else if (shouldPlay) {
            awaitingResume = false
            if (!serviceRequested) {
                // Android 17 requires a mediaPlayback FGS to start while the
                // UI is visible. Never start one after the Activity stopped.
                val activity = webView.context as? MainActivity
                if (
                    activity
                        ?.lifecycle
                        ?.currentState
                        ?.isAtLeast(Lifecycle.State.STARTED) == true
                ) {
                    startService()
                } else {
                    pauseMedia()
                }
            }
        } else if (!pausedByNotification && !awaitingResume) {
            // An ordinary website pause ends the session. Keep the session
            // only for a user-requested pause, so Play can resume it.
            stopService()
        }
        if (serviceRequested) {
            BackgroundMediaPlaybackService.updatePlaying(
                shouldPlay && !pausedByNotification
            )
        }
    }

    private fun startService() {
        val previous = activeController?.get()
        if (previous != null && previous !== this) {
            previous.stopPlayback(userInitiated = false)
        }
        serviceRequested = true
        activeController = WeakReference(this)
        try {
            ContextCompat.startForegroundService(
                context,
                BackgroundMediaPlaybackService.startIntent(context),
            )
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to start background media playback", error)
            // Without an active mediaPlayback service, background playback
            // would be silenced on Android 17. Do not leave media unguarded.
            stopPlayback(userInitiated = false)
        }
    }

    private fun stopService() {
        serviceRequested = false
        if (activeController?.get() === this) {
            activeController = null
            context.stopService(BackgroundMediaPlaybackService.startIntent(context))
        }
    }

    private fun pauseMedia(retainResumePosition: Boolean = false) {
        if (!disposed) {
            webView.evaluateJavascript(
                if (retainResumePosition) {
                    PAUSE_FOR_NOTIFICATION_SCRIPT
                } else {
                    PAUSE_MEDIA_SCRIPT
                },
                null,
            )
        }
    }

    private fun pauseFromNotificationInternal() {
        if (disposed || !serviceRequested) {
            return
        }
        pausedByNotification = true
        awaitingResume = false
        frames.clear()
        pauseMedia(retainResumePosition = true)
        BackgroundMediaPlaybackService.updatePlaying(false)
    }

    private fun resumeFromNotificationInternal() {
        if (disposed || !serviceRequested || !pausedByNotification) {
            return
        }
        pausedByNotification = false
        // Do not report playing before an HTMLMediaElement actually resumes.
        // Websites may reject script-initiated play() without a user gesture.
        awaitingResume = true
        webView.evaluateJavascript(RESUME_MEDIA_SCRIPT, null)
    }

    private fun stopPlayback(userInitiated: Boolean) {
        stoppedByUser = userInitiated
        pausedByNotification = false
        awaitingResume = false
        frames.clear()
        pauseMedia()
        stopService()
    }

    fun dispose() {
        if (disposed) {
            return
        }
        stopPlayback(userInitiated = false)
        disposed = true
        webView.removeJavascriptInterface(BRIDGE_NAME)
    }
}

/** Stop HTML media and clear any media queued for notification resume. */
internal const val PAUSE_MEDIA_SCRIPT =
    """
    (function () {
      if (typeof window.__wvkControlMedia === 'function') {
        window.__wvkControlMedia('stop');
        return;
      }
      // The document-start script is unavailable on some old WebViews.
      function pauseFrame(win) {
        try {
          win.document.querySelectorAll('audio,video').forEach(function (media) {
            media.pause();
          });
          win.document.querySelectorAll('iframe').forEach(function (frame) {
            try { pauseFrame(frame.contentWindow); } catch (ignored) {}
            try { frame.contentWindow.postMessage({ __wvkMediaAction: 'stop' }, '*'); } catch (ignored) {}
          });
        } catch (ignored) {}
      }
      pauseFrame(window);
    })();
    """

/** Save only currently playing elements, then pause them. */
internal const val PAUSE_FOR_NOTIFICATION_SCRIPT =
    """
    (function () {
      if (typeof window.__wvkControlMedia === 'function') {
        window.__wvkControlMedia('pause');
      }
    })();
    """

/** Resume only elements that the notification previously paused. */
internal const val RESUME_MEDIA_SCRIPT =
    """
    (function () {
      if (typeof window.__wvkControlMedia === 'function') {
        window.__wvkControlMedia('resume');
      }
    })();
    """

/**
 * Installed in all available WebView frames (not just the top-level page).
 *
 * On sites that explicitly pause video when their document becomes hidden, retaining
 * the WebView and foreground service alone is insufficient. Keep the visibility
 * signal visible *only during active media playback* for supported YouTube pages.
 * This is opt-in and scoped to the current document, with no extraction,
 * replacement, or additional media player.
 *
 * See https://developer.mozilla.org/en-US/docs/Web/API/Page_Visibility_API and
 * https://developer.android.com/reference/android/webkit/WebView.
 */
internal const val DOCUMENT_START_SCRIPT =
    """
    (function () {
      if (window.__wvkBackgroundMediaInstalled) return;
      window.__wvkBackgroundMediaInstalled = true;
      var host = window.location.hostname.toLowerCase();
      var isYouTube = /(^|\.)youtube(-nocookie)?\.com$/.test(host);
      var frameId = Date.now().toString(36) + '-' + Math.random().toString(36);
      var lastPlaying = null;
      var pausedForNotification = [];
      var resumingFromNotification = false;
      function keepPlaybackVisible() {
        return lastPlaying === true || resumingFromNotification;
      }

      if (isYouTube) {
        // YouTube's site can pause an otherwise-running video when a WebView
        // becomes hidden. Retain the original values when no media is playing,
        // so site navigation, ordinary pauses, and background loading work.
        function nativeVisibilityGetter(name, fallback) {
          var prototype = document;
          while (prototype) {
            var property = Object.getOwnPropertyDescriptor(prototype, name);
            if (property && typeof property.get === 'function') {
              return function () { return property.get.call(document); };
            }
            prototype = Object.getPrototypeOf(prototype);
          }
          return function () { return fallback; };
        }
        var getHidden = nativeVisibilityGetter('hidden', document.hidden);
        var getVisibilityState = nativeVisibilityGetter('visibilityState', document.visibilityState);
        try {
          Object.defineProperty(document, 'hidden', {
            configurable: true,
            get: function () { return keepPlaybackVisible() ? false : getHidden(); }
          });
          Object.defineProperty(document, 'visibilityState', {
            configurable: true,
            get: function () { return keepPlaybackVisible() ? 'visible' : getVisibilityState(); }
          });
        } catch (ignored) {
          // Some WebView versions may not permit shadowing these properties.
        }
        function preservePlaybackVisibility(event) {
          if (keepPlaybackVisible() && event && event.stopImmediatePropagation) {
            event.stopImmediatePropagation();
          }
        }
        window.addEventListener('visibilitychange', preservePlaybackVisibility, true);
        document.addEventListener('visibilitychange', preservePlaybackVisibility, true);
      }

      function report() {
        var playing = Array.prototype.some.call(
          document.querySelectorAll('audio,video'),
          function (media) { return !media.paused && !media.ended; }
        );
        if (playing === lastPlaying) return;
        lastPlaying = playing;
        try {
          window.WebviewKioskBackgroundMedia.onPlaybackStateChanged(frameId, playing);
        } catch (ignored) {}
      }
      function forEachFrame(action) {
        document.querySelectorAll('iframe').forEach(function (frame) {
          try { frame.contentWindow.postMessage({ __wvkMediaAction: action }, '*'); }
          catch (ignored) {}
        });
      }
      window.__wvkControlMedia = function (action) {
        if (action === 'pause') {
          resumingFromNotification = false;
          document.querySelectorAll('audio,video').forEach(function (media) {
            if (!media.paused && !media.ended) {
              if (pausedForNotification.indexOf(media) === -1) pausedForNotification.push(media);
              media.pause();
            }
          });
        } else if (action === 'resume') {
          var previouslyPlaying = pausedForNotification.slice();
          pausedForNotification.length = 0;
          // A video site can check document.hidden during a play() request.
          // Keep visibility available until the pending play promises settle.
          resumingFromNotification = previouslyPlaying.length > 0;
          var pending = [];
          previouslyPlaying.forEach(function (media) {
            if (!media.isConnected) return;
            try {
              var result = media.play();
              if (result && typeof result.catch === 'function') {
                pending.push(result.catch(function () {
                  // A website may require a fresh tap before playback can resume.
                  if (pausedForNotification.indexOf(media) === -1) pausedForNotification.push(media);
                }));
              }
            } catch (ignored) {
              if (pausedForNotification.indexOf(media) === -1) pausedForNotification.push(media);
            }
          });
          Promise.all(pending).then(function () { resumingFromNotification = false; });
        } else if (action === 'stop') {
          resumingFromNotification = false;
          pausedForNotification.length = 0;
          document.querySelectorAll('audio,video').forEach(function (media) {
            media.pause();
          });
        } else {
          return;
        }
        forEachFrame(action);
        report();
      };
      window.__wvkReportMediaPlayback = function () { lastPlaying = null; report(); };
      ['play','playing','pause','ended','emptied','abort'].forEach(function (event) {
        document.addEventListener(event, report, true);
      });
      document.addEventListener('DOMContentLoaded', report);
      document.addEventListener('visibilitychange', function () {
        lastPlaying = null; report();
      });
      window.addEventListener('pagehide', function () {
        try {
          window.WebviewKioskBackgroundMedia.onPlaybackStateChanged(frameId, false);
        } catch (ignored) {}
      });
      window.addEventListener('message', function (event) {
        // Only accept commands sent by the embedding parent, never messages
        // from arbitrary ads, sibling frames, or external windows.
        if (window.parent !== window && event.source === window.parent &&
            event.data && typeof event.data.__wvkMediaAction === 'string') {
          window.__wvkControlMedia(event.data.__wvkMediaAction);
        }
      });
      report();
    })();
    """
