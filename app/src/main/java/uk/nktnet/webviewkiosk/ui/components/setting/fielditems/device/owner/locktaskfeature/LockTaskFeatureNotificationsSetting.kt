package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureNotificationsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.Owner.LockTaskFeature.NOTIFICATIONS

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_owner_lock_task_feature_notifications_title),
        infoText = """
            Enables notifications for all apps.

            This shows notification icons in the status bar, heads-up notifications, and
            the expandable notification shade.

            If you enable this button, you must also enable the Home button.

            Tapping notification actions and buttons that open new panels doesn't
            work in lock task mode.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureNotifications,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.lockTaskFeatureNotifications = it },
    )
}
