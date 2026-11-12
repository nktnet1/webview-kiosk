package uk.nktnet.webviewkiosk.services

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.utils.webview.MediaPlaybackInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackgroundMediaNotificationTest {
    @Test
    fun notificationUsesNativeMediaStyleWithTransportControls() {
        val controller =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
                .create()
        try {
            val service = controller.get()
            BackgroundMediaPlaybackService.updateMediaInfo(
                MediaPlaybackInfo(
                    title = "Test video",
                    artist = "Test channel",
                    durationMs = 20_000L,
                    positionMs = 10_000L,
                    canSeek = true,
                    canPrevious = true,
                    canNext = true,
                )
            )
            val notification = service.buildNotification()
            assertEquals(
                Notification.MediaStyle::class.java.name,
                notification.extras.getString(Notification.EXTRA_TEMPLATE),
            )
            assertEquals(
                "Test video",
                notification.extras.getString(Notification.EXTRA_TITLE),
            )
            assertEquals(
                "Test channel",
                notification.extras.getString(Notification.EXTRA_TEXT),
            )
            val actions = notification.actions.map { it.title.toString() }
            assertTrue(
                actions.contains(
                    service.getString(R.string.notification_background_media_play)
                )
            )
            assertTrue(
                actions.contains(
                    service.getString(
                        R.string.notification_background_media_previous
                    )
                )
            )
            assertTrue(
                actions.contains(
                    service.getString(R.string.notification_background_media_next)
                )
            )
            assertTrue(
                actions.contains(
                    service.getString(R.string.notification_background_media_stop)
                )
            )
            assertNotNull(notification.contentIntent)
        } finally {
            controller.destroy()
        }
    }
}
