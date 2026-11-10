package uk.nktnet.webviewkiosk.utils.webview

import android.util.Log
import android.webkit.JavascriptInterface
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
    }

    private class MediaStateBridge(
        private val controller: BackgroundMediaPlaybackController
    ) {
        @JavascriptInterface
        fun onPlaybackStateChanged(frameId: String, playing: Boolean) {
            // JavascriptInterface callbacks are invoked on a private WebView
            // thread; all WebView and service operations belong on the UI thread.
            controller.webView.post {
                controller.onPlaybackStateChanged(frameId, playing)
            }
        }
    }

    private val context = webView.context.applicationContext
    private val frames = PlayingMediaFrames()
    private var disposed = false
    private var serviceRequested = false
    private var stoppedByUser = false
    private val bridge = MediaStateBridge(this)

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
        if (!shouldPlay) {
            stopService()
        } else if (stoppedByUser) {
            pauseMedia()
        } else if (!serviceRequested) {
            // Android 17 requires that a mediaPlayback FGS be started while
            // the UI is visible to inherit while-in-use capabilities.
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

    private fun pauseMedia() {
        if (!disposed) {
            webView.evaluateJavascript(PAUSE_MEDIA_SCRIPT, null)
        }
    }

    private fun stopPlayback(userInitiated: Boolean) {
        stoppedByUser = userInitiated
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

/** Pause ordinary HTML media without destroying the page or losing its position. */
internal const val PAUSE_MEDIA_SCRIPT =
    """
    (function () {
      function pauseFrame(win) {
        try {
          win.document.querySelectorAll('audio,video').forEach(function (media) {
            media.pause();
          });
          win.document.querySelectorAll('iframe').forEach(function (frame) {
            try { pauseFrame(frame.contentWindow); } catch (ignored) {}
            try { frame.contentWindow.postMessage('__wvkStopMedia', '*'); } catch (ignored) {}
          });
        } catch (ignored) {}
      }
      pauseFrame(window);
    })();
    """

/**
 * Installed in all available WebView frames. A frame reports only its own
 * HTMLMediaElements, so pausing one iframe cannot mask another active player.
 */
internal const val DOCUMENT_START_SCRIPT =
    """
    (function () {
      if (window.__wvkBackgroundMediaInstalled) return;
      window.__wvkBackgroundMediaInstalled = true;
      var frameId = Date.now().toString(36) + '-' + Math.random().toString(36);
      var lastPlaying = null;
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
        if (event.data !== '__wvkStopMedia') return;
        document.querySelectorAll('audio,video').forEach(function (media) {
          media.pause();
        });
        document.querySelectorAll('iframe').forEach(function (frame) {
          try { frame.contentWindow.postMessage('__wvkStopMedia', '*'); } catch (ignored) {}
        });
      });
      report();
    })();
    """
