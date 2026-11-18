package uk.nktnet.webviewkiosk.ui.components.setting

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.utils.PermissionState

class NotificationPermissionControlsTest {
    @Test
    fun warnsForNonExemptForegroundNotificationsOnlyWhenEnabledWithoutAccess() {
        // Media-session notifications are exempt; MQTT uses this warning.
        assertTrue(
            shouldWarnForForegroundServiceNotifications(
                serviceEnabled = true,
                notificationsAvailable = false,
            )
        )
        assertFalse(
            shouldWarnForForegroundServiceNotifications(
                serviceEnabled = true,
                notificationsAvailable = true,
            )
        )
        assertFalse(
            shouldWarnForForegroundServiceNotifications(
                serviceEnabled = false,
                notificationsAvailable = false,
            )
        )
        assertFalse(
            shouldWarnForForegroundServiceNotifications(
                serviceEnabled = false,
                notificationsAvailable = true,
            )
        )
    }

    @Test
    fun buttonRequestsRuntimePermissionOnAndroid13WhenPromptCanBeShown() {
        assertEquals(
            R.string.ui_request_notification_permission,
            notificationPermissionButtonLabel(
                Build.VERSION_CODES.TIRAMISU,
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
            notificationPermissionButtonLabel(
                Build.VERSION_CODES.TIRAMISU,
                deniedWithRationale,
                false,
            ),
        )
        assertEquals(
            R.string.ui_enable_in_app_info,
            notificationPermissionButtonLabel(
                Build.VERSION_CODES.S_V2,
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
                Build.VERSION_CODES.TIRAMISU,
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
                Build.VERSION_CODES.TIRAMISU,
                PermissionState(granted = true, shouldShowRationale = false),
                notificationsAvailable = true,
            ),
        )
    }
}
