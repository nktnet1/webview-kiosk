package uk.nktnet.webviewkiosk.services

import android.app.Notification
import android.media.session.MediaSession
import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.utils.webview.MediaPlaybackInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class BackgroundMediaNotificationTest {
    @Test
    @Config(sdk = [23, 28])
    fun notificationKeepsTrackSlotsAndCloseLastForEveryCapabilityCombination() {
        val controller =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
                .create()
        try {
            val service = controller.get()
            for (previous in listOf(false, true)) {
                for (next in listOf(false, true)) {
                    BackgroundMediaPlaybackService.updateMediaInfo(
                        MediaPlaybackInfo(
                            title = "Test video",
                            artist = "Test channel",
                            durationMs = 20_000L,
                            positionMs = 10_000L,
                            canSeek = true,
                            canChangeSpeed = true,
                            canPrevious = previous,
                            canNext = next,
                        )
                    )
                    for (playing in listOf(false, true)) {
                        BackgroundMediaPlaybackService.updatePlaying(playing)
                        val notification = service.buildNotification()
                        assertEquals(
                            Notification.MediaStyle::class.java.name,
                            notification.extras.getString(
                                Notification.EXTRA_TEMPLATE
                            ),
                        )
                        assertEquals(
                            "Test video",
                            notification.extras.getString(Notification.EXTRA_TITLE),
                        )
                        assertEquals(
                            "Test channel",
                            notification.extras.getString(Notification.EXTRA_TEXT),
                        )
                        assertEquals(
                            listOf(
                                    if (previous) {
                                        R.string
                                            .notification_background_media_previous
                                    } else {
                                        R.string
                                            .notification_background_media_previous_unavailable
                                    },
                                    if (playing) {
                                        R.string.notification_background_media_pause
                                    } else {
                                        R.string.notification_background_media_play
                                    },
                                    if (next) {
                                        R.string.notification_background_media_next
                                    } else {
                                        R.string
                                            .notification_background_media_next_unavailable
                                    },
                                    R.string.notification_background_media_stop,
                                )
                                .map(service::getString),
                            notification.actions.map { it.title.toString() },
                        )
                        assertEquals(
                            previous,
                            notification.actions[0].actionIntent != null,
                        )
                        assertNotNull(notification.actions[1].actionIntent)
                        assertEquals(
                            next,
                            notification.actions[2].actionIntent != null,
                        )
                        assertNotNull(notification.actions[3].actionIntent)
                        assertEquals(
                            listOf(0, 1, 2),
                            notification.extras
                                .getIntArray(Notification.EXTRA_COMPACT_ACTIONS)!!
                                .toList(),
                        )
                        assertNotNull(notification.contentIntent)
                        assertNotNull(notification.deleteIntent)
                    }
                }
            }
        } finally {
            controller.destroy()
        }
    }

    @Test
    @Config(sdk = [33])
    fun semanticControlsKeepUnavailableSkipPlaceholdersBeforeClose() {
        val controller =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
                .create()
        try {
            val service = controller.get()
            val session =
                service.javaClass
                    .getDeclaredField("mediaSession")
                    .apply { isAccessible = true }
                    .get(service) as MediaSession
            // Robolectric does not connect MediaController's shadow to its session.
            // Read the state and callback installed on the real MediaSession.
            val callbackHandler: Any =
                ReflectionHelpers.getField(session, "mCallback")
            val callback: MediaSession.Callback =
                ReflectionHelpers.getField(callbackHandler, "mCallback")
            for (previous in listOf(false, true)) {
                for (next in listOf(false, true)) {
                    BackgroundMediaPlaybackService.updateMediaInfo(
                        MediaPlaybackInfo(
                            durationMs = 20_000L,
                            canSeek = true,
                            canChangeSpeed = true,
                            canPrevious = previous,
                            canNext = next,
                        )
                    )
                    val state: PlaybackState =
                        ReflectionHelpers.getField(session, "mPlaybackState")
                    assertEquals(
                        previous,
                        state.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS !=
                            0L,
                    )
                    assertEquals(
                        next,
                        state.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                    )
                    assertTrue(state.actions and PlaybackState.ACTION_SEEK_TO != 0L)
                    assertEquals(
                        0L,
                        state.actions and
                            (PlaybackState.ACTION_REWIND or
                                PlaybackState.ACTION_FAST_FORWARD or
                                PlaybackState.ACTION_SET_PLAYBACK_SPEED),
                    )
                    val expected = buildList {
                        if (!previous) {
                            add("MEDIA_PREVIOUS_UNAVAILABLE")
                        }
                        if (!next) {
                            add("MEDIA_NEXT_UNAVAILABLE")
                        }
                        add("MEDIA_STOP")
                    }
                    assertEquals(
                        expected,
                        state.customActions.map {
                            it.action.substringAfterLast('.')
                        },
                    )
                    for (action in state.customActions.dropLast(1)) {
                        callback.onCustomAction(action.action, null)
                    }
                    assertFalse(shadowOf(service).isStoppedBySelf)
                }
            }
            callback.onCustomAction("uk.nktnet.webviewkiosk.action.MEDIA_STOP", null)
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
    }

    @Test
    @Config(sdk = [31, 33])
    fun foregroundNotificationIsNotDeferredByAndroid() {
        val controller =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
                .create()
        try {
            assertEquals(
                Notification.FOREGROUND_SERVICE_IMMEDIATE,
                ReflectionHelpers.getField<Int>(
                    controller.get().buildNotification(),
                    "mFgsDeferBehavior",
                ),
            )
        } finally {
            controller.destroy()
        }
    }
}
