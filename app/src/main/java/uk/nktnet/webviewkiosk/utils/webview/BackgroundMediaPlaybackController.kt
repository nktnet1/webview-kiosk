package uk.nktnet.webviewkiosk.utils.webview

import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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

    fun contains(frameId: String): Boolean = frameId in frames

    fun firstPlayingFrame(): String? = frames.firstOrNull()

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
 * A YouTube pause when Home is pressed must not immediately tear down the media
 * foreground service: that would expose Chromium to the hidden-window signal before
 * the page has a chance to recover. Keep a bounded grace period, also for foreground
 * pauses so an immediate Home gesture cannot race the bridge.
 */
internal object YouTubeBackgroundPausePolicy {
    const val FOREGROUND_GRACE_MS = 1_500L
    const val BACKGROUND_GRACE_MS = 32_000L

    fun gracePeriod(
        wasYouTubePlaying: Boolean,
        serviceRequested: Boolean,
        userStopped: Boolean,
        background: Boolean,
    ): Long? =
        if (wasYouTubePlaying && serviceRequested && !userStopped) {
            if (background) {
                BACKGROUND_GRACE_MS
            } else {
                FOREGROUND_GRACE_MS
            }
        } else {
            null
        }
}

/**
 * WebView receives window visibility separately from onPause(). Chromium may suspend
 * video as soon as the host window becomes GONE/INVISIBLE, even if WebView.onPause()
 * is never called. Defer that signal for an active media session and replay it when
 * the session ends while the Activity is hidden.
 *
 * See https://issues.chromium.org/issues/494455605.
 */
internal class BackgroundMediaWindowVisibility {
    private var deferredVisibility: Int? = null

    /** Returns true if Chromium's window-visibility update was deferred. */
    fun dispatch(
        visibility: Int,
        retainForMedia: Boolean,
        notifyWebView: (Int) -> Unit,
    ): Boolean {
        if (retainForMedia && visibility != View.VISIBLE) {
            deferredVisibility = visibility
            return true
        }
        deferredVisibility = null
        notifyWebView(visibility)
        return false
    }

    /** Restores the actual hidden state after Stop, navigation, or media end. */
    fun release(notifyWebView: (Int) -> Unit): Boolean {
        val visibility = deferredVisibility ?: return false
        deferredVisibility = null
        notifyWebView(visibility)
        return true
    }
}

/**
 * Keeps the existing WebView as the media player. The foreground service only
 * supplies process priority and a user-visible way to stop background playback; it
 * cannot recreate a website's player if Android destroys its Activity.
 */
internal class BackgroundMediaPlaybackController(
    private val webView: WebView,
    private val isActive: () -> Boolean,
    private val releaseWindowVisibility: () -> Unit,
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

        fun currentMediaInfo(): MediaPlaybackInfo? =
            activeController?.get()?.currentMediaInfo

        fun currentPageHost(): String? =
            activeController?.get()?.webView?.url?.toUri()?.host

        fun previousFromNotification() {
            activeController?.get()?.sendMediaCommand("previous")
        }

        fun nextFromNotification() {
            activeController?.get()?.sendMediaCommand("next")
        }

        fun seekFromNotification(positionMs: Long) {
            activeController?.get()?.sendMediaCommand("seek", positionMs)
        }

        fun rewindFromNotification() {
            activeController?.get()?.sendMediaCommand("rewind")
        }

        fun forwardFromNotification() {
            activeController?.get()?.sendMediaCommand("forward")
        }

        fun speedFromNotification() {
            activeController?.get()?.sendMediaCommand("speed")
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
    private var wasYouTubePlaying = false
    private var hostInBackground = false
    private var pendingPauseStop: Runnable? = null
    private var activeFrameId: String? = null
    private var currentMediaInfo: MediaPlaybackInfo? = null
    private val mediaByFrame = mutableMapOf<String, MediaPlaybackInfo>()
    private val bridge =
        MediaStateBridge(
            webView,
            ::onPlaybackStateChanged,
            ::onMediaInfoChanged,
            ::onUserInteraction,
        )

    /** Keep the renderer's last visible state only during an active FGS session. */
    fun shouldRetainWindowVisibility(): Boolean =
        !disposed && serviceRequested && isActive()

    init {
        // Keep Chromium's renderer at important priority after Home. The
        // WebView default waives its renderer priority while not visible,
        // which can terminate background audio even with a media FGS.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(
                WebView.RENDERER_PRIORITY_IMPORTANT,
                false,
            )
        }
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
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    YOUTUBE_BACKGROUND_RECOVERY_SCRIPT,
                    setOf("*"),
                )
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    RICH_MEDIA_SCRIPT,
                    setOf("*"),
                )
            }
        }
    }

    fun onNavigationStarted() {
        if (!disposed) {
            bridge.invalidatePendingReports()
            cancelPendingPauseStop()
            wasYouTubePlaying = false
            stoppedByUser = false
            pausedByNotification = false
            awaitingResume = false
            frames.clear()
            activeFrameId = null
            currentMediaInfo = null
            mediaByFrame.clear()
            stopService()
        }
    }

    fun onPageFinished() {
        if (!disposed) {
            // Older Android WebView providers do not support document-start
            // scripts. This fallback covers media in the main document.
            webView.evaluateJavascript(DOCUMENT_START_SCRIPT, null)
            webView.evaluateJavascript(YOUTUBE_BACKGROUND_RECOVERY_SCRIPT, null)
            webView.evaluateJavascript(RICH_MEDIA_SCRIPT, null)
            publishHostBackground()
        }
    }

    /**
     * Called before the Activity's window is hidden, not after WebView is paused.
     */
    fun onHostBackgroundChanged(background: Boolean) {
        if (disposed || hostInBackground == background) {
            return
        }
        hostInBackground = background
        Log.d(TAG, "Host background=$background, mediaSession=$serviceRequested")
        publishHostBackground()
        // A pause reported just before Home must get the longer recovery window.
        if (pendingPauseStop != null) {
            scheduleYouTubePauseStop(forceReschedule = true)
        }
    }

    private fun publishHostBackground() {
        webView.evaluateJavascript(
            "window.__wvkSetHostBackground && window.__wvkSetHostBackground($hostInBackground);",
            null,
        )
    }

    fun onActivityStarted() {
        if (!disposed) {
            onHostBackgroundChanged(false)
            webView.evaluateJavascript(
                "window.__wvkReportMediaPlayback && window.__wvkReportMediaPlayback();",
                null,
            )
        }
    }

    /**
     * A fresh foreground gesture may restart media after notification Pause/Stop.
     */
    internal fun onUserInteraction() {
        if (!disposed && isActive() && !hostInBackground) {
            stoppedByUser = false
            pausedByNotification = false
        }
    }

    private fun onPlaybackStateChanged(frameId: String, playing: Boolean) {
        if (disposed || !isActive()) {
            return
        }
        if (playing && (stoppedByUser || pausedByNotification)) {
            // Reject blocked autoplay before it can replace the paused player
            // or register a new frame as the notification's transport target.
            pauseMedia(retainResumePosition = pausedByNotification)
            return
        }
        val shouldPlay = frames.update(frameId, playing)
        // Keep the first still-playing document selected; muted ad frames must
        // not steal notification metadata or transport controls mid-video.
        if (activeFrameId == null || !frames.contains(activeFrameId!!)) {
            // Retain the last playing frame while its media session survives a
            // pause (for example, YouTube auto-pausing on Home). Notification
            // Play must still be able to target that player even though the
            // frame is no longer in the currently playing set.
            val preferred =
                frames.firstPlayingFrame()
                    ?: activeFrameId.takeIf { serviceRequested }
            if (activeFrameId != preferred) {
                activeFrameId = preferred
                currentMediaInfo = preferred?.let(mediaByFrame::get)
                if (serviceRequested) {
                    // A new frame may not have reported metadata yet. Do not
                    // leave the previous player's title or capabilities visible.
                    BackgroundMediaPlaybackService.updateMediaInfo(
                        currentMediaInfo ?: MediaPlaybackInfo()
                    )
                }
            }
        }
        if (playing && (frameId.startsWith("yt-") || isYouTubePage())) {
            wasYouTubePlaying = true
        }
        if (shouldPlay) {
            cancelPendingPauseStop()
        }
        if (shouldPlay) {
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
            // YouTube may issue its own pause as Home hides the Activity.
            // Keep the FGS and Chromium visibility until the injected player
            // guard can try recovering. Normal websites still stop promptly.
            if (
                YouTubeBackgroundPausePolicy.gracePeriod(
                    wasYouTubePlaying,
                    serviceRequested,
                    stoppedByUser,
                    hostInBackground,
                ) != null
            ) {
                scheduleYouTubePauseStop()
            } else {
                stopService()
            }
        }
        if (serviceRequested) {
            BackgroundMediaPlaybackService.updatePlaying(
                shouldPlay && !pausedByNotification
            )
        }
    }

    private fun onMediaInfoChanged(frameId: String, payload: String) {
        if (disposed || !isActive() || frameId.isBlank() || frameId.length > 128) {
            return
        }
        val info = MediaPlaybackInfo.parse(payload) ?: return
        // Do not let inactive advertising frames replace the playing video's data.
        mediaByFrame[frameId] = info
        if (mediaByFrame.size > 24) {
            mediaByFrame.keys
                .firstOrNull { it != activeFrameId }
                ?.let(mediaByFrame::remove)
        }
        if (activeFrameId == frameId) {
            currentMediaInfo = info
            if (serviceRequested) {
                BackgroundMediaPlaybackService.updateMediaInfo(info)
            }
        }
    }

    private fun sendMediaCommand(command: String, positionMs: Long = 0L) {
        if (disposed || !serviceRequested) {
            return
        }
        val info = currentMediaInfo ?: return
        val allowed =
            when (command) {
                "previous" -> info.canPrevious
                "next" -> info.canNext
                "seek",
                "rewind",
                "forward" -> info.canSeek
                "speed" -> info.canChangeSpeed
                else -> false
            }
        val frameId = activeFrameId ?: return
        if (!allowed || !frameId.matches(Regex("[A-Za-z0-9.-]{1,128}"))) {
            return
        }
        val point =
            if (command == "seek") {
                positionMs.coerceIn(0L, info.durationMs)
            } else {
                0L
            }
        webView.evaluateJavascript(
            "window.__wvkControlRichMedia && window.__wvkControlRichMedia('$command','$frameId',$point);",
            null,
        )
    }

    private fun isYouTubePage(): Boolean {
        val host = webView.url?.toUri()?.host?.lowercase() ?: return false
        return host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" ||
            host.endsWith(".youtube-nocookie.com")
    }

    private fun cancelPendingPauseStop() {
        pendingPauseStop?.let(webView::removeCallbacks)
        pendingPauseStop = null
    }

    private fun scheduleYouTubePauseStop(forceReschedule: Boolean = false) {
        if (pendingPauseStop != null && !forceReschedule) {
            return
        }
        cancelPendingPauseStop()
        val delay =
            YouTubeBackgroundPausePolicy.gracePeriod(
                wasYouTubePlaying,
                serviceRequested,
                stoppedByUser,
                hostInBackground,
            ) ?: return
        Log.d(TAG, "YouTube paused, preserving media session for ${delay}ms")
        val task = Runnable {
            pendingPauseStop = null
            if (!frames.isPlaying && !pausedByNotification && !awaitingResume) {
                Log.d(TAG, "YouTube did not resume; ending background media session")
                stopService()
            }
        }
        pendingPauseStop = task
        webView.postDelayed(task, delay)
    }

    private fun startService() {
        val previous = activeController?.get()
        if (previous != null && previous !== this) {
            previous.stopPlayback(userInitiated = false)
        }
        serviceRequested = true
        activeController = WeakReference(this)
        currentMediaInfo?.let(BackgroundMediaPlaybackService::updateMediaInfo)
        Log.d(TAG, "Starting foreground service for WebView media")
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

    private fun stopService(releaseVisibility: Boolean = true) {
        cancelPendingPauseStop()
        wasYouTubePlaying = false
        activeFrameId = null
        currentMediaInfo = null
        mediaByFrame.clear()
        val wasRequested = serviceRequested
        serviceRequested = false
        if (activeController?.get() === this) {
            activeController = null
            context.stopService(BackgroundMediaPlaybackService.startIntent(context))
        }
        if (wasRequested && releaseVisibility) {
            // If Home already hid the Activity, deliver the deferred visibility
            // change now so an idle WebView does not stay artificially visible.
            releaseWindowVisibility()
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
        bridge.invalidatePendingReports()
        cancelPendingPauseStop()
        pausedByNotification = true
        awaitingResume = false
        frames.clear()
        pauseMedia(retainResumePosition = true)
        BackgroundMediaPlaybackService.updatePlaying(false)
    }

    private fun resumeFromNotificationInternal() {
        if (
            disposed ||
                !serviceRequested ||
                (!pausedByNotification && frames.isPlaying)
        ) {
            return
        }
        pausedByNotification = false
        // Do not report playing before an HTMLMediaElement actually resumes.
        // Websites may reject script-initiated play() without a user gesture.
        awaitingResume = true
        val frame = activeFrameId
        if (frame != null && frame.matches(Regex("[A-Za-z0-9.-]{1,128}"))) {
            webView.evaluateJavascript(
                "window.__wvkControlRichMedia && window.__wvkControlRichMedia('play','$frame',0);",
                null,
            )
        } else {
            webView.evaluateJavascript(RESUME_MEDIA_SCRIPT, null)
        }
    }

    private fun stopPlayback(userInitiated: Boolean) {
        bridge.invalidatePendingReports()
        cancelPendingPauseStop()
        stoppedByUser = userInitiated
        pausedByNotification = false
        awaitingResume = false
        frames.clear()
        pauseMedia()
        stopService()
    }

    fun dispose(rendererGone: Boolean = false) {
        if (disposed) {
            return
        }
        bridge.dispose()
        if (rendererGone) {
            // onRenderProcessGone must not evaluate JS or notify the dead
            // renderer about deferred window visibility.
            frames.clear()
            stopService(releaseVisibility = false)
        } else {
            stopPlayback(userInitiated = false)
        }
        disposed = true
        if (!rendererGone) {
            webView.removeJavascriptInterface(BRIDGE_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Restore the framework policy if the WebView is reused.
                webView.setRendererPriorityPolicy(
                    WebView.RENDERER_PRIORITY_IMPORTANT,
                    true,
                )
            }
        }
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
      var frameId = (isYouTube ? 'yt-' : '') + Date.now().toString(36) + '-' + Math.random().toString(36);
      window.__wvkFrameId = frameId;
      var lastPlaying = null;
      var pausedForNotification = [];
      var resumingFromNotification = false;
      var commandGeneration = 0;
      var mediaVersions = new WeakMap();
      // A reused video element can now contain a different clip or stream.
      // Keep transport intent scoped to the resource that actually played.
      function captureMedia(media) {
        if (!media) return null;
        return {
          media: media, currentSrc: media.currentSrc || '', src: media.src || '',
          srcObject: media.srcObject || null, version: mediaVersions.get(media) || 0
        };
      }
      function matchesMedia(entry, media) {
        return !!entry && !!media && media.isConnected && entry.media === media &&
          entry.currentSrc === (media.currentSrc || '') && entry.src === (media.src || '') &&
          entry.srcObject === (media.srcObject || null) &&
          entry.version === (mediaVersions.get(media) || 0);
      }
      window.__wvkMediaIdentity = { capture: captureMedia, matches: matchesMedia };
      function savePausedMedia(media) {
        pausedForNotification = pausedForNotification.filter(function (entry) {
          return entry.media !== media;
        });
        pausedForNotification.push(captureMedia(media));
      }
      document.addEventListener('emptied', function (event) {
        var media = event.target;
        mediaVersions.set(media, (mediaVersions.get(media) || 0) + 1);
        pausedForNotification = pausedForNotification.filter(function (entry) {
          return entry.media !== media;
        });
      }, true);
      window.__wvkHostBackground = false;
      function setHostBackground(hidden) {
        window.__wvkHostBackground = hidden === true;
        document.querySelectorAll('iframe').forEach(function (frame) {
          try {
            frame.contentWindow.postMessage({ __wvkMediaHostHidden: hidden }, '*');
          } catch (ignored) {}
        });
      }
      window.__wvkSetHostBackground = setHostBackground;
      function keepPlaybackVisible() {
        return lastPlaying === true || resumingFromNotification ||
          (isYouTube && typeof window.__wvkKeepYoutubeVisible === 'function' &&
           window.__wvkKeepYoutubeVisible());
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

      // Stop must not permanently disable playback: only an actual user
      // interaction can arm a new session after notification Stop.
      function reportUserInteraction(event) {
        if (!event.isTrusted) return;
        try { window.WebviewKioskBackgroundMedia.onUserInteraction(); }
        catch (ignored) {}
      }
      ['pointerdown', 'keydown', 'click'].forEach(function (name) {
        document.addEventListener(name, reportUserInteraction, true);
      });

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
      window.__wvkControlMedia = function (action, localOnly) {
        if (action !== 'pause' && action !== 'resume' && action !== 'stop') return;
        var generation = ++commandGeneration;
        if (isYouTube && typeof window.__wvkYoutubeExplicitAction === 'function') {
          window.__wvkYoutubeExplicitAction(action);
        }
        if (action === 'pause') {
          resumingFromNotification = false;
          document.querySelectorAll('audio,video').forEach(function (media) {
            if (!media.paused && !media.ended) {
              savePausedMedia(media);
              media.pause();
            }
          });
        } else if (action === 'resume') {
          var previouslyPlaying = pausedForNotification.filter(function (entry) {
            return matchesMedia(entry, entry.media);
          });
          pausedForNotification.length = 0;
          // A video site can check document.hidden during a play() request.
          // Keep visibility available until the pending play promises settle.
          resumingFromNotification = previouslyPlaying.length > 0;
          var pending = [];
          previouslyPlaying.forEach(function (entry) {
            var media = entry.media;
            if (!matchesMedia(entry, media)) return;
            try {
              var result = media.play();
              if (result && typeof result.catch === 'function') {
                pending.push(result.catch(function () {
                  // A website may require a fresh tap before playback can resume.
                  if (generation === commandGeneration && matchesMedia(entry, media)) savePausedMedia(media);
                }));
              }
            } catch (ignored) {
              if (generation === commandGeneration && matchesMedia(entry, media)) savePausedMedia(media);
            }
          });
          Promise.all(pending).then(function () {
            if (generation === commandGeneration) resumingFromNotification = false;
          });
        } else if (action === 'stop') {
          resumingFromNotification = false;
          pausedForNotification.length = 0;
          document.querySelectorAll('audio,video').forEach(function (media) {
            media.pause();
          });
        }
        if (localOnly !== true) forEachFrame(action);
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
        if (window.parent !== window && event.source === window.parent && event.data) {
          if (typeof event.data.__wvkMediaHostHidden === 'boolean') {
            setHostBackground(event.data.__wvkMediaHostHidden);
          }
          if (typeof event.data.__wvkMediaAction === 'string') {
            window.__wvkControlMedia(event.data.__wvkMediaAction);
          }
        }
      });
      report();
    })();
    """

/**
 * YouTube-specific safety net. A normal HTML media pause is not proof that the user
 * paused: YouTube can pause its own player when Android backgrounds the WebView.
 * Recover only playback already observed before Home, never a player paused by the
 * user or from a notification. Keep attempts finite.
 *
 * Inspired by the behaviour documented in NouTube (AGPL-3.0), implemented
 * independently so no NouTube source is incorporated into the application.
 */
internal const val YOUTUBE_BACKGROUND_RECOVERY_SCRIPT =
    """
    (function () {
      if (window.__wvkYoutubeRecoveryInstalled) return;
      var host = window.location.hostname.toLowerCase();
      if (!/(^|\.)youtube(-nocookie)?\.com$/.test(host)) return;
      if (!window.__wvkMediaIdentity) return;
      window.__wvkYoutubeRecoveryInstalled = true;
      var wasPlaying = false;
      var lastPlayback = null;
      var mediaIdentity = window.__wvkMediaIdentity;
      var explicitlyPaused = false;
      var attemptCount = 0;
      var audioBusyTicks = 0;
      var maxAttempts = 4;
      var maxAudioBusyTicks = 8;

      function isBackground() { return window.__wvkHostBackground === true; }
      function isShorts() { return /^\/shorts(?:\/|$)/.test(window.location.pathname || ''); }
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
        var active = shortsVideo();
        if (active && typeof active.closest === 'function') {
          var root = active.closest('#movie_player, #shorts-player, .html5-video-player');
          if (root) return root;
        }
        return document.getElementById('movie_player');
      }
      function video() {
        var active = shortsVideo();
        if (active) return active;
        var root = player();
        return (root && root.querySelector('video')) || document.querySelector('video');
      }
      function state() {
        if (isShorts()) {
          var short = video();
          if (short) return short.ended ? 0 : (short.paused ? 2 : 1);
        }
        var element = player();
        try {
          if (element && typeof element.getPlayerState === 'function') {
            return element.getPlayerState();
          }
        } catch (ignored) {}
        var media = video();
        return media && !media.paused && !media.ended ? 1 : 2;
      }
      // Our native notification commands and the page's Media Session handlers
      // express intentional transport controls and must never be undone.
      window.__wvkYoutubeExplicitAction = function (action) {
        if (action === 'pause' || action === 'stop') {
          explicitlyPaused = true;
        } else if (action === 'resume') {
          explicitlyPaused = false;
          attemptCount = 0;
          audioBusyTicks = 0;
          wasPlaying = mediaIdentity.matches(lastPlayback, video());
        }
      };
      window.__wvkKeepYoutubeVisible = function () {
        return isBackground() && wasPlaying && !explicitlyPaused &&
          mediaIdentity.matches(lastPlayback, video()) &&
          attemptCount < maxAttempts && audioBusyTicks < maxAudioBusyTicks;
      };

      function trackPlay(event) {
        var media = video();
        if (!media || media.paused || media.ended || (event && event.target !== media)) return;
        lastPlayback = mediaIdentity.capture(media);
        wasPlaying = true;
        explicitlyPaused = false;
        attemptCount = 0;
        audioBusyTicks = 0;
      }
      document.addEventListener('playing', trackPlay, true);
      document.addEventListener('play', trackPlay, true);
      document.addEventListener('pause', function (event) {
        if (isBackground()) return;
        var root = player();
        if (event.target && (event.target === video() || (root && root.contains(event.target)))) {
          explicitlyPaused = true;
        }
      }, true);

      // Install before YouTube registers these callbacks. A headset or system
      // media-control Pause may otherwise be mistaken for a site-initiated pause.
      try {
        var mediaSession = window.navigator && window.navigator.mediaSession;
        if (mediaSession && typeof mediaSession.setActionHandler === 'function') {
          window.__wvkYoutubeMediaSession = mediaSession;
          var originalSetActionHandler = mediaSession.setActionHandler.bind(mediaSession);
          mediaSession.setActionHandler = function (action, handler) {
            if (typeof handler !== 'function' || (action !== 'play' && action !== 'pause')) {
              return originalSetActionHandler(action, handler);
            }
            return originalSetActionHandler(action, function (details) {
              window.__wvkYoutubeExplicitAction(action === 'pause' ? 'pause' : 'resume');
              return handler(details);
            });
          };
        }
      } catch (ignored) {}

      if (state() === 1) trackPlay();
      window.setInterval(function () {
        var current = state();
        if (current === 1) {
          // A delayed pause from a notification must not be cancelled by a
          // final playing-state sample before the media element actually stops.
          if (!explicitlyPaused) {
            var playingMedia = video();
            if (playingMedia && !playingMedia.paused && !playingMedia.ended) {
              lastPlayback = mediaIdentity.capture(playingMedia);
              wasPlaying = true;
              attemptCount = 0;
            }
          }
          return;
        }
        if (!isBackground()) {
          if (current === 2) wasPlaying = false;
          attemptCount = 0;
          audioBusyTicks = 0;
          return;
        }
        if (current !== 2 || !wasPlaying || explicitlyPaused || attemptCount >= maxAttempts) {
          return;
        }
        var media = video();
        if (!media || media.ended || !mediaIdentity.matches(lastPlayback, media)) {
          wasPlaying = false;
          return;
        }
        // A focus loss may have paused the stream. Wait for any existing
        // music activity to clear instead of fighting phone calls or another
        // application. Android can report our own just-paused stream as active
        // briefly, hence the bounded wait before abandoning recovery.
        try {
          if (window.WebviewKioskBackgroundMedia &&
              !window.WebviewKioskBackgroundMedia.canAutoResume()) {
            audioBusyTicks++;
            return;
          }
        } catch (ignored) { return; }
        audioBusyTicks = 0;
        attemptCount++;
        try {
          var element = player();
          if (element && typeof element.playVideo === 'function') {
            element.playVideo();
          } else if (typeof media.play === 'function') {
            var promise = media.play();
            if (promise && typeof promise.catch === 'function') promise.catch(function () {});
          }
        } catch (ignored) {}
      }, 2500);
    })();
    """
