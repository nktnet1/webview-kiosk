package uk.nktnet.webviewkiosk.services

import android.app.Notification
import android.media.session.MediaSession
import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun notificationKeepsFiveSlotsWithMuteFirstAndCloseLast() {
        val controller =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
                .create()
        try {
            val service = controller.get()
            for (previous in listOf(false, true)) {
                for (next in listOf(false, true)) {
                    for (position in listOf(0L, 10_000L)) {
                        BackgroundMediaPlaybackService.updateMediaInfo(
                            MediaPlaybackInfo(
                                title = "Test video",
                                artist = "Test channel",
                                durationMs = 20_000L,
                                positionMs = position,
                                canSeek = true,
                                canChangeSpeed = true,
                                canMute = true,
                                canPrevious = previous,
                                canNext = next,
                            )
                        )
                        val previousEnabled = previous || position > 0
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
                                notification.extras.getString(
                                    Notification.EXTRA_TITLE
                                ),
                            )
                            assertEquals(
                                "Test channel",
                                notification.extras.getString(
                                    Notification.EXTRA_TEXT
                                ),
                            )
                            val labels =
                                listOf(
                                    R.string.notification_background_media_mute,
                                    if (previousEnabled) {
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
                            assertEquals(
                                labels.map(service::getString),
                                notification.actions.map { it.title.toString() },
                            )
                            assertEquals(
                                listOf(true, previousEnabled, true, next, true),
                                notification.actions.map { it.actionIntent != null },
                            )
                            assertEquals(
                                listOf(1, 2, 3),
                                notification.extras
                                    .getIntArray(
                                        Notification.EXTRA_COMPACT_ACTIONS
                                    )!!
                                    .toList(),
                            )
                            assertNotNull(notification.contentIntent)
                            assertNotNull(notification.deleteIntent)
                        }
                    }
                }
            }
        } finally {
            controller.destroy()
        }
    }

    @Test
    @Config(sdk = [33, 37])
    fun semanticControlsFillBothExtraSlotsWithMuteThenClose() {
        val controller =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
                .create()
        try {
            val service = controller.get()
            val session: MediaSession =
                ReflectionHelpers.getField(service, "mediaSession")
            // Robolectric does not connect MediaController's shadow to its session.
            val callbackHandler: Any =
                ReflectionHelpers.getField(session, "mCallback")
            val callback: MediaSession.Callback =
                ReflectionHelpers.getField(callbackHandler, "mCallback")
            for (previous in listOf(false, true)) {
                for (next in listOf(false, true)) {
                    for (position in listOf(0L, 10_000L)) {
                        BackgroundMediaPlaybackService.updateMediaInfo(
                            MediaPlaybackInfo(
                                durationMs = 20_000L,
                                positionMs = position,
                                canSeek = true,
                                canChangeSpeed = true,
                                canMute = true,
                                canPrevious = previous,
                                canNext = next,
                            )
                        )
                        val previousEnabled = previous || position > 0
                        val state: PlaybackState =
                            ReflectionHelpers.getField(session, "mPlaybackState")
                        assertEquals(
                            previousEnabled,
                            state.actions and
                                PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                        )
                        assertEquals(
                            next,
                            state.actions and PlaybackState.ACTION_SKIP_TO_NEXT !=
                                0L,
                        )
                        assertTrue(
                            state.actions and PlaybackState.ACTION_SEEK_TO != 0L
                        )
                        assertEquals(
                            0L,
                            state.actions and
                                (PlaybackState.ACTION_REWIND or
                                    PlaybackState.ACTION_FAST_FORWARD or
                                    PlaybackState.ACTION_SET_PLAYBACK_SPEED),
                        )
                        val expected = buildList {
                            if (!previousEnabled) {
                                add("MEDIA_PREVIOUS_UNAVAILABLE")
                            }
                            if (!next) {
                                add("MEDIA_NEXT_UNAVAILABLE")
                            }
                            add("MEDIA_MUTE")
                            add("MEDIA_STOP")
                        }
                        assertEquals(
                            expected,
                            state.customActions.map {
                                it.action.substringAfterLast('.')
                            },
                        )
                        for (action in state.customActions.dropLast(2)) {
                            callback.onCustomAction(action.action, null)
                        }
                        assertFalse(shadowOf(service).isStoppedBySelf)
                        assertEquals(
                            R.drawable.media_close,
                            state.customActions.last().icon,
                        )
                    }
                }
            }
            callback.onCustomAction("uk.nktnet.webviewkiosk.action.MEDIA_STOP", null)
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun muteStateChangesLabelAndIconWithoutMovingOtherControls() {
        val controller =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
                .create()
        try {
            val service = controller.get()
            val unavailable = service.buildNotification().actions[0]
            assertNull(unavailable.actionIntent)
            assertEquals(R.drawable.media_volume_disabled, unavailable.icon)
            for (muted in listOf(false, true, false)) {
                BackgroundMediaPlaybackService.updateMediaInfo(
                    MediaPlaybackInfo(canMute = true, muted = muted)
                )
                val actions = service.buildNotification().actions
                assertEquals(5, actions.size)
                assertNotNull(actions[0].actionIntent)
                assertEquals(
                    service.getString(
                        if (muted) {
                            R.string.notification_background_media_unmute
                        } else {
                            R.string.notification_background_media_mute
                        }
                    ),
                    actions[0].title,
                )
                assertEquals(
                    if (muted) {
                        R.drawable.media_volume_off
                    } else {
                        R.drawable.media_volume_on
                    },
                    actions[0].icon,
                )
                assertEquals(R.drawable.media_close, actions.last().icon)
            }
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
