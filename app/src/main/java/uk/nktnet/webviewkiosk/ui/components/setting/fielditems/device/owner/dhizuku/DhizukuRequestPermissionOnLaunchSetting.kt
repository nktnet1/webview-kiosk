package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.dhizuku

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun DhizukuRequestPermissionOnLaunchSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.Owner.Dhizuku.REQUEST_PERMISSION_ON_LAUNCH

    BooleanSettingFieldItem(
        label = "Request Permission on Launch",
        infoText = """
            When enabled, if Dhizuku is installed and has not granted ${Constants.APP_NAME}
            access to Device Owner privileges, prompt the user for permission.
        """.trimIndent(),
        initialValue = userSettings.dhizukuRequestPermissionOnLaunch,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.dhizukuRequestPermissionOnLaunch = it },
    )
}
