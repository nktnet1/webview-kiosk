package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureNotificationsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable Notifications",
        infoText = """
            Enables notifications for all apps.

            This shows notification icons in the status bar, heads-up notifications, and
            the expandable notification shade. 

            If you enable this button, you must also enable the Home button. 

            Tapping notification actions and buttons that open new panels doesn’t
            work in lock task mode.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureNotifications,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.NOTIFICATIONS),
        onSave = { userSettings.lockTaskFeatureNotifications = it },
    )
}
