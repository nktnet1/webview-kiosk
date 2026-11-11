package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMediaPlaybackSettingTest {
    @Test
    fun warnsOnlyWhenPlaybackEnabledAndNotificationsUnavailable() {
        assertTrue(shouldShowMediaNotificationWarning(true, false))
        assertFalse(shouldShowMediaNotificationWarning(true, true))
        assertFalse(shouldShowMediaNotificationWarning(false, false))
        assertFalse(shouldShowMediaNotificationWarning(false, true))
    }
}
