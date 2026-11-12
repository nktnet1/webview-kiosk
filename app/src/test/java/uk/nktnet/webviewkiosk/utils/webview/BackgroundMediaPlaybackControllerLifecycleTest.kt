package uk.nktnet.webviewkiosk.utils.webview

import android.app.Notification
import android.webkit.WebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.services.BackgroundMediaPlaybackService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackgroundMediaPlaybackControllerLifecycleTest {
    @Test
    fun notificationStopRequiresFreshForegroundGestureBeforePageCanPlayAgain() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        val controller = BackgroundMediaPlaybackController(webView, { true }, {})
        val stopped =
            controller.javaClass.getDeclaredField("stoppedByUser").apply {
                isAccessible = true
            }
        try {
            stopped.setBoolean(controller, true)
            controller.onActivityStarted()
            assertTrue(stopped.getBoolean(controller))
            controller.onUserInteraction()
            assertFalse(stopped.getBoolean(controller))

            stopped.setBoolean(controller, true)
            controller.onHostBackgroundChanged(true)
            controller.onUserInteraction()
            assertTrue(stopped.getBoolean(controller))
        } finally {
            controller.dispose()
            webView.destroy()
        }
    }

    @Test
    fun foregroundGestureAllowsPagePlaybackAfterNotificationPause() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        var active = true
        val controller = BackgroundMediaPlaybackController(webView, { active }, {})
        val paused =
            controller.javaClass.getDeclaredField("pausedByNotification").apply {
                isAccessible = true
            }
        try {
            paused.setBoolean(controller, true)
            controller.onHostBackgroundChanged(true)
            controller.onUserInteraction()
            assertTrue(paused.getBoolean(controller))

            controller.onHostBackgroundChanged(false)
            active = false
            controller.onUserInteraction()
            assertTrue(paused.getBoolean(controller))

            active = true
            controller.onUserInteraction()
            assertFalse(paused.getBoolean(controller))
        } finally {
            controller.dispose()
            webView.destroy()
        }
    }

    @Test
    fun youtubeAutoPauseRetainsTheFrameForNotificationPlay() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        val controller = BackgroundMediaPlaybackController(webView, { true }, {})
        val activeFrame =
            controller.javaClass.getDeclaredField("activeFrameId").apply {
                isAccessible = true
            }
        val currentInfo =
            controller.javaClass.getDeclaredField("currentMediaInfo").apply {
                isAccessible = true
            }
        try {
            controller.javaClass
                .getDeclaredField("serviceRequested")
                .apply { isAccessible = true }
                .setBoolean(controller, true)
            controller.javaClass
                .getDeclaredField("wasYouTubePlaying")
                .apply { isAccessible = true }
                .setBoolean(controller, true)
            controller.javaClass
                .getDeclaredField("hostInBackground")
                .apply { isAccessible = true }
                .setBoolean(controller, true)
            val frames =
                controller.javaClass
                    .getDeclaredField("frames")
                    .apply { isAccessible = true }
                    .get(controller) as PlayingMediaFrames
            frames.update("yt-active-video", true)
            activeFrame.set(controller, "yt-active-video")
            currentInfo.set(controller, MediaPlaybackInfo(title = "Playing video"))

            controller.javaClass
                .getDeclaredMethod(
                    "onPlaybackStateChanged",
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                )
                .apply { isAccessible = true }
                .invoke(controller, "yt-active-video", false)

            assertFalse(frames.isPlaying)
            assertEquals("yt-active-video", activeFrame.get(controller))
            assertEquals(
                "Playing video",
                (currentInfo.get(controller) as MediaPlaybackInfo).title,
            )
        } finally {
            controller.dispose()
            webView.destroy()
        }
    }

    @Test
    fun blockedAutoplayCannotReplaceTheNotificationPauseTarget() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        val controller = BackgroundMediaPlaybackController(webView, { true }, {})
        try {
            field(controller, "serviceRequested").setBoolean(controller, true)
            field(controller, "pausedByNotification").setBoolean(controller, true)
            field(controller, "activeFrameId").set(controller, "main-player")
            field(controller, "currentMediaInfo")
                .set(controller, MediaPlaybackInfo(title = "Original video"))

            report(controller, "autoplay-frame", true)

            assertEquals(
                "main-player",
                field(controller, "activeFrameId").get(controller),
            )
            assertEquals(
                "Original video",
                (field(controller, "currentMediaInfo").get(controller)
                        as MediaPlaybackInfo)
                    .title,
            )
            assertFalse(
                (field(controller, "frames").get(controller) as PlayingMediaFrames)
                    .isPlaying
            )
        } finally {
            controller.dispose()
            webView.destroy()
        }
    }

    @Test
    fun switchingToAFrameWithoutMetadataClearsPreviousNotificationDetails() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        val controller = BackgroundMediaPlaybackController(webView, { true }, {})
        val serviceController =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
                .create()
        val service = serviceController.get()
        try {
            field(controller, "serviceRequested").setBoolean(controller, true)
            field(controller, "activeFrameId").set(controller, "old-player")
            val frames =
                field(controller, "frames").get(controller) as PlayingMediaFrames
            frames.update("old-player", true)
            frames.update("new-player", true)
            BackgroundMediaPlaybackService.updateMediaInfo(
                MediaPlaybackInfo(
                    title = "Old video",
                    durationMs = 60_000L,
                    canSeek = true,
                )
            )

            report(controller, "old-player", false)

            val notification = service.buildNotification()
            assertEquals(
                "new-player",
                field(controller, "activeFrameId").get(controller),
            )
            assertEquals(
                service.getString(R.string.notification_background_media_title),
                notification.extras.getString(Notification.EXTRA_TITLE),
            )
            assertFalse(
                notification.actions.any {
                    it.title ==
                        service.getString(
                            R.string.notification_background_media_rewind
                        )
                }
            )
        } finally {
            controller.dispose()
            serviceController.destroy()
            webView.destroy()
        }
    }

    private fun field(controller: BackgroundMediaPlaybackController, name: String) =
        controller.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun report(
        controller: BackgroundMediaPlaybackController,
        frameId: String,
        playing: Boolean,
    ) {
        controller.javaClass
            .getDeclaredMethod(
                "onPlaybackStateChanged",
                String::class.java,
                Boolean::class.javaPrimitiveType,
            )
            .apply { isAccessible = true }
            .invoke(controller, frameId, playing)
    }

    @Test
    fun rendererDeathDoesNotDeliverDeferredVisibilityToDeadWebView() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        var releaseCalled = false
        val controller =
            BackgroundMediaPlaybackController(webView, { true }) {
                releaseCalled = true
            }
        // Simulate a playback session whose renderer has just died.
        controller.javaClass
            .getDeclaredField("serviceRequested")
            .apply {
                isAccessible = true
            }
            .setBoolean(controller, true)
        try {
            controller.dispose(rendererGone = true)
            assertFalse(releaseCalled)
        } finally {
            webView.destroy()
        }
    }
}
