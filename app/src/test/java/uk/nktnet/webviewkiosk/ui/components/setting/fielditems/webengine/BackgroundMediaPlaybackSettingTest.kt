package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMediaPlaybackSettingTest {
    @Test
    fun warnsOnlyWhenPlaybackEnabledAndNotificationsUnavailable() {
        assertTrue(
            shouldShowMediaNotificationWarning(
                playbackEnabled = true,
                notificationsAvailable = false,
            )
        )
        assertFalse(
            shouldShowMediaNotificationWarning(
                playbackEnabled = true,
                notificationsAvailable = true,
            )
        )
        assertFalse(
            shouldShowMediaNotificationWarning(
                playbackEnabled = false,
                notificationsAvailable = false,
            )
        )
        assertFalse(
            shouldShowMediaNotificationWarning(
                playbackEnabled = false,
                notificationsAvailable = true,
            )
        )
    }
}
