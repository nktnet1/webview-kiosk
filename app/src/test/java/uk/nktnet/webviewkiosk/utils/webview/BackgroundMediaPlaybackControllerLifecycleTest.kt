package uk.nktnet.webviewkiosk.utils.webview

import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import android.webkit.WebView
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.services.BackgroundMediaPlaybackService
import uk.nktnet.webviewkiosk.states.LockStateSingleton

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class BackgroundMediaPlaybackControllerLifecycleTest {
    @Test
    fun notificationPauseClearsLoadingAndRejectsLateBufferingReports() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        val controller = BackgroundMediaPlaybackController(webView, { true }, {})
        val service =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
                .create()
        try {
            field(controller, "serviceRequested").setBoolean(controller, true)
            field(controller, "activeFrameId").set(controller, "player")
            val buffering = MediaPlaybackInfo(buffering = true, positionMs = 0)
            field(controller, "currentMediaInfo").set(controller, buffering)
            BackgroundMediaPlaybackService.updateMediaInfo(buffering)
            controller.javaClass
                .getDeclaredMethod("pauseFromNotificationInternal")
                .apply { isAccessible = true }
                .invoke(controller)
            assertFalse(
                (field(controller, "currentMediaInfo").get(controller)
                        as MediaPlaybackInfo)
                    .buffering
            )
            val cached =
                field(controller, "mediaByFrame").get(controller) as Map<*, *>
            assertFalse((cached["player"] as MediaPlaybackInfo).buffering)

            controller.javaClass
                .getDeclaredMethod(
                    "onMediaInfoChanged",
                    String::class.java,
                    String::class.java,
                )
                .apply { isAccessible = true }
                .invoke(
                    controller,
                    "player",
                    """{"buffering":true,"positionMs":0}""",
                )
            assertFalse(
                (field(controller, "currentMediaInfo").get(controller)
                        as MediaPlaybackInfo)
                    .buffering
            )
            assertEquals(
                service.get().getString(R.string.notification_background_media_play),
                service.get().buildNotification().actions[2].title,
            )
            assertTrue(controller.shouldRetainWindowVisibility())
        } finally {
            controller.dispose()
            service.destroy()
            webView.destroy()
        }
    }

    @Test
    fun nativeCommandsAllowRestartAndMuteOnlyForSupportedPlayers() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        val controller = BackgroundMediaPlaybackController(webView, { true }, {})
        try {
            field(controller, "serviceRequested").setBoolean(controller, true)
            field(controller, "activeFrameId").set(controller, "active-player")
            val command =
                controller.javaClass
                    .getDeclaredMethod(
                        "sendMediaCommand",
                        String::class.java,
                        Long::class.javaPrimitiveType,
                    )
                    .apply { isAccessible = true }
            val start =
                MediaPlaybackInfo(
                    durationMs = 60_000,
                    positionMs = 0,
                    canSeek = true,
                )
            field(controller, "currentMediaInfo").set(controller, start)
            command.invoke(controller, "previous", 0L)
            command.invoke(controller, "mute", 0L)
            assertNull(shadowOf(webView).lastEvaluatedJavascript)

            field(controller, "currentMediaInfo")
                .set(controller, start.copy(positionMs = 500, canMute = true))
            command.invoke(controller, "previous", 0L)
            assertEquals(
                "window.__wvkControlRichMedia && window.__wvkControlRichMedia('previous','active-player',0);",
                shadowOf(webView).lastEvaluatedJavascript,
            )
            command.invoke(controller, "mute", 0L)
            val muteScript = shadowOf(webView).lastEvaluatedJavascript
            assertEquals(
                "window.__wvkControlRichMedia && window.__wvkControlRichMedia('mute','active-player',0);",
                muteScript,
            )

            field(controller, "currentMediaInfo")
                .set(controller, start.copy(positionMs = 500, canSeek = false))
            command.invoke(controller, "previous", 0L)
            command.invoke(controller, "mute", 0L)
            assertEquals(muteScript, shadowOf(webView).lastEvaluatedJavascript)
        } finally {
            controller.dispose()
            webView.destroy()
        }
    }

    @Test
    fun foregroundPlaybackStartsNotificationAndPagePauseKeepsIt() {
        val app = RuntimeEnvironment.getApplication()
        UserSettings(app).apply {
            backgroundMediaPlayback = true
            mqttEnabled = false
            lockOnLaunch = false
            homeUrl = "about:blank"
            resetOnLaunch = false
            resetOnInactivitySeconds = 0
        }
        // This fixture exercises playback, without starting the global lock monitor.
        val monitoringStarted =
            LockStateSingleton::class.java.getDeclaredField("isStarted").apply {
                isAccessible = true
            }
        val wasMonitoring = monitoringStarted.getBoolean(LockStateSingleton)
        monitoringStarted.setBoolean(LockStateSingleton, true)
        WebViewStartup.setGateForTests(WebViewStartupGate(beginStartup = {}))
        val activity =
            Robolectric.buildActivity(MainActivity::class.java)
                .create()
                .start()
                .resume()
        val webView = WebView(activity.get())
        val controller = BackgroundMediaPlaybackController(webView, { true }, {})
        val service =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
        try {
            report(controller, "foreground-player", true)
            assertTrue(BackgroundMediaPlaybackController.isPlaybackRequested())
            assertTrue(BackgroundMediaPlaybackController.isPlaying())
            assertFalse(field(controller, "hostInBackground").getBoolean(controller))
            service.create()
            service
                .get()
                .onStartCommand(
                    BackgroundMediaPlaybackService.startIntent(app),
                    0,
                    1,
                )
            val foreground = shadowOf(service.get())
            assertTrue(foreground.isLastForegroundNotificationAttached)
            assertEquals(
                app.getString(R.string.notification_background_media_pause),
                foreground.lastForegroundNotification.actions[2].title,
            )

            report(controller, "foreground-player", false)
            assertTrue(BackgroundMediaPlaybackController.isPlaybackRequested())
            assertFalse(BackgroundMediaPlaybackController.isPlaying())
            assertFalse(foreground.isStoppedBySelf)
            assertEquals(
                app.getString(R.string.notification_background_media_play),
                shadowOf(app.getSystemService(NotificationManager::class.java))
                    .getNotification(
                        CustomNotificationType.BACKGROUND_MEDIA_PLAYBACK
                    )
                    .actions[2]
                    .title,
            )

            BackgroundMediaPlaybackController.stopFromNotification()
            assertFalse(BackgroundMediaPlaybackController.isPlaybackRequested())
            assertFalse(controller.shouldRetainWindowVisibility())
        } finally {
            controller.dispose()
            service.destroy()
            webView.destroy()
            activity.pause().stop().destroy()
            WebViewStartup.setGateForTests(null)
            monitoringStarted.setBoolean(LockStateSingleton, wasMonitoring)
        }
    }

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
    fun pagePausesRetainSessionAndResumeTargetWithoutATimeout() {
        for (frameId in listOf("html-player", "yt-active-video")) {
            for (background in listOf(false, true)) {
                val webView = WebView(RuntimeEnvironment.getApplication())
                var releases = 0
                val controller =
                    BackgroundMediaPlaybackController(webView, { true }) {
                        releases++
                    }
                try {
                    field(controller, "serviceRequested")
                        .setBoolean(controller, true)
                    report(controller, frameId, true)
                    field(controller, "currentMediaInfo")
                        .set(controller, MediaPlaybackInfo(title = "Paused video"))
                    controller.onHostBackgroundChanged(background)

                    report(controller, frameId, false)
                    // Exceed both the former foreground and YouTube background
                    // grace.
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))

                    assertTrue(controller.shouldRetainWindowVisibility())
                    assertEquals(0, releases)
                    assertFalse(
                        (field(controller, "frames").get(controller)
                                as PlayingMediaFrames)
                            .isPlaying
                    )
                    assertEquals(
                        frameId,
                        field(controller, "activeFrameId").get(controller),
                    )
                    assertEquals(
                        "Paused video",
                        (field(controller, "currentMediaInfo").get(controller)
                                as MediaPlaybackInfo)
                            .title,
                    )

                    // Navigating away must still end a paused session.
                    controller.onNavigationStarted()
                    assertFalse(controller.shouldRetainWindowVisibility())
                    assertNull(field(controller, "activeFrameId").get(controller))
                    assertEquals(1, releases)
                } finally {
                    controller.dispose()
                    webView.destroy()
                }
            }
        }
    }

    @Test
    fun notificationPauseKeepsTheSessionUntilClose() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        val controller = BackgroundMediaPlaybackController(webView, { true }, {})
        try {
            field(controller, "serviceRequested").setBoolean(controller, true)
            report(controller, "html-player", true)
            controller.javaClass
                .getDeclaredMethod("pauseFromNotificationInternal")
                .apply { isAccessible = true }
                .invoke(controller)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
            assertTrue(controller.shouldRetainWindowVisibility())
            assertTrue(
                field(controller, "pausedByNotification").getBoolean(controller)
            )
            assertEquals(
                "html-player",
                field(controller, "activeFrameId").get(controller),
            )

            controller.javaClass
                .getDeclaredMethod("stopPlayback", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(controller, true)
            assertFalse(controller.shouldRetainWindowVisibility())
            assertNull(field(controller, "activeFrameId").get(controller))
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
                    canPrevious = true,
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
            assertNull(notification.actions[1].actionIntent)
        } finally {
            controller.dispose()
            serviceController.destroy()
            webView.destroy()
        }
    }

    private fun field(controller: BackgroundMediaPlaybackController, name: String) =
        controller.javaClass.getDeclaredField(name).apply { isAccessible = true }

    @Test
    fun mutedAutoplayCannotKeepControlsWhenAnUnmutedPlayerStarts() {
        for (metadataFirst in listOf(false, true)) {
            val webView = WebView(RuntimeEnvironment.getApplication())
            val controller = BackgroundMediaPlaybackController(webView, { true }, {})
            try {
                field(controller, "serviceRequested").setBoolean(controller, true)
                reportInfo(controller, "muted-ad", """{"title":"Ad","muted":true}""")
                report(controller, "muted-ad", true)
                assertEquals(
                    "muted-ad",
                    field(controller, "activeFrameId").get(controller),
                )

                val mainInfo =
                    """{"title":"Selected video","muted":false,"canNext":true}"""
                if (metadataFirst) {
                    reportInfo(controller, "main-player", mainInfo)
                }
                report(controller, "main-player", true)
                if (!metadataFirst) {
                    reportInfo(controller, "main-player", mainInfo)
                }

                assertEquals(
                    "main-player",
                    field(controller, "activeFrameId").get(controller),
                )
                assertEquals(
                    "Selected video",
                    (field(controller, "currentMediaInfo").get(controller)
                            as MediaPlaybackInfo)
                        .title,
                )
                controller.javaClass
                    .getDeclaredMethod("pauseFromNotificationInternal")
                    .apply { isAccessible = true }
                    .invoke(controller)
                controller.javaClass
                    .getDeclaredMethod("resumeFromNotificationInternal")
                    .apply { isAccessible = true }
                    .invoke(controller)
                assertEquals(
                    "window.__wvkControlRichMedia && window.__wvkControlRichMedia('play','main-player',0);",
                    shadowOf(webView).lastEvaluatedJavascript,
                )
            } finally {
                controller.dispose()
                webView.destroy()
            }
        }
    }

    @Test
    fun mutingTheSelectedPlayerDoesNotTransferItsControlsToAnotherFrame() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        val controller = BackgroundMediaPlaybackController(webView, { true }, {})
        try {
            field(controller, "serviceRequested").setBoolean(controller, true)
            reportInfo(
                controller,
                "main-player",
                """{"title":"Selected video","muted":false}""",
            )
            report(controller, "main-player", true)
            reportInfo(
                controller,
                "other-frame",
                """{"title":"Other video","muted":false}""",
            )
            report(controller, "other-frame", true)
            reportInfo(
                controller,
                "main-player",
                """{"title":"Selected video","muted":true}""",
            )

            assertEquals(
                "main-player",
                field(controller, "activeFrameId").get(controller),
            )
            assertTrue(
                (field(controller, "currentMediaInfo").get(controller)
                        as MediaPlaybackInfo)
                    .muted
            )
        } finally {
            controller.dispose()
            webView.destroy()
        }
    }

    private fun reportInfo(
        controller: BackgroundMediaPlaybackController,
        frameId: String,
        payload: String,
    ) {
        controller.javaClass
            .getDeclaredMethod(
                "onMediaInfoChanged",
                String::class.java,
                String::class.java,
            )
            .apply { isAccessible = true }
            .invoke(controller, frameId, payload)
    }

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
