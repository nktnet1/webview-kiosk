package uk.nktnet.webviewkiosk.ui.components.setting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.utils.PermissionState

class NotificationPermissionControlsTest {
    @Test
    fun warnsForEitherForegroundServiceOnlyWhenEnabledWithoutNotificationAccess() {
        // The condition is shared by media and MQTT, not tied to Device > Allow
        // Notifications.
        assertTrue(shouldWarnForForegroundServiceNotifications(true, false))
        assertFalse(shouldWarnForForegroundServiceNotifications(true, true))
        assertFalse(shouldWarnForForegroundServiceNotifications(false, false))
        assertFalse(shouldWarnForForegroundServiceNotifications(false, true))
    }

    @Test
    fun buttonRequestsRuntimePermissionOnAndroid13WhenPromptCanBeShown() {
        assertEquals(
            R.string.ui_request_notification_permission,
            notificationPermissionButtonLabel(
                33,
                PermissionState(granted = false, shouldShowRationale = false),
                notificationsAvailable = false,
            ),
        )
    }

    @Test
    fun buttonOpensAppSettingsWhenPermissionNeedsManualEnablement() {
        val deniedWithRationale =
            PermissionState(granted = false, shouldShowRationale = true)
        assertEquals(
            R.string.ui_enable_in_app_info,
            notificationPermissionButtonLabel(33, deniedWithRationale, false),
        )
        assertEquals(
            R.string.ui_enable_in_app_info,
            notificationPermissionButtonLabel(
                32,
                PermissionState(granted = false, shouldShowRationale = false),
                notificationsAvailable = false,
            ),
        )
    }

    @Test
    fun buttonOpensSettingsIfAndroidNotificationsAreDisabledDespitePermissionGrant() {
        assertEquals(
            R.string.ui_enable_in_app_info,
            notificationPermissionButtonLabel(
                33,
                PermissionState(granted = true, shouldShowRationale = false),
                notificationsAvailable = false,
            ),
        )
    }

    @Test
    fun buttonRetainsSystemSettingsActionWhenNotificationsGranted() {
        assertEquals(
            R.string.ui_disable_in_app_info,
            notificationPermissionButtonLabel(
                33,
                PermissionState(granted = true, shouldShowRationale = false),
                notificationsAvailable = true,
            ),
        )
    }
}
