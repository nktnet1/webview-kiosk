package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.states.KeepScreenOnStateSingleton
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun KeepScreenOnSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Keep Screen On",
        infoText = "Enable this option to keep your device awake (no screen timeout).",
        initialValue = userSettings.keepScreenOn,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.KEEP_SCREEN_ON),
        onSave = {
            userSettings.keepScreenOn = it
            KeepScreenOnStateSingleton.setKeepScreenOn(it)
        }
    )
}
