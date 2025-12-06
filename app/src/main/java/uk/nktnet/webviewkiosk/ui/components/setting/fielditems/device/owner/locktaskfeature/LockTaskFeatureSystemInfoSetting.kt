package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureSystemInfoSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Show System Info",
        infoText = """
            Enables the status bar's system info area that contains indicators
            such as connectivity, battery, and sound/vibrate options.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureSystemInfo,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.SYSTEM_INFO),
        onSave = { userSettings.lockTaskFeatureSystemInfo = it },
    )
}
