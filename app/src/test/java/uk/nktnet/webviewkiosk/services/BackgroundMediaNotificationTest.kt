package uk.nktnet.webviewkiosk.services

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    // Notification has no non-deprecated API to inspect the RemoteViews selected
    // by setCustomContentView/setCustomBigContentView. Keep the assertion rather
    // than weakening coverage of the actual built notification.
    @Suppress("DEPRECATION")
    @Test
    fun flatNotificationUsesCustomControlsInsteadOfSystemMediaStyle() {
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
            val notification = service.buildNotification(flatLayout = true)
            assertEquals(
                Notification.DecoratedCustomViewStyle::class.java.name,
                notification.extras.getString(Notification.EXTRA_TEMPLATE),
            )
            assertEquals(
                R.layout.notification_media_compact,
                notification.contentView.layoutId,
            )
            assertEquals(
                R.layout.notification_media_expanded,
                notification.bigContentView.layoutId,
            )
            assertNotNull(notification.contentIntent)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun olderAndroidRetainsNativeMediaStyle() {
        val controller =
            Robolectric.buildService(BackgroundMediaPlaybackService::class.java)
                .create()
        try {
            val notification = controller.get().buildNotification(flatLayout = false)
            assertEquals(
                Notification.MediaStyle::class.java.name,
                notification.extras.getString(Notification.EXTRA_TEMPLATE),
            )
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun flatProgressIsBoundedAndAbsentForUnseekableMedia() {
        assertNull(flatMediaProgress(MediaPlaybackInfo()))
        assertNull(
            flatMediaProgress(
                MediaPlaybackInfo(durationMs = 10_000L, positionMs = 5_000L)
            )
        )
        val info =
            MediaPlaybackInfo(
                durationMs = 10_000L,
                positionMs = 5_000L,
                canSeek = true,
            )
        assertEquals(50, flatMediaProgress(info))
        assertEquals(0, flatMediaProgress(info.copy(positionMs = 0L)))
        assertEquals(100, flatMediaProgress(info.copy(positionMs = 20_000L)))
    }
}
