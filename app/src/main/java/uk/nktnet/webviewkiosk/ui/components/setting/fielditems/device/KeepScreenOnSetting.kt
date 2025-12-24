package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.states.KeepScreenOnStateSingleton
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun KeepScreenOnSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.KEEP_SCREEN_ON

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_keep_screen_on_title),
        infoText = "Enable this option to keep your device awake (no screen timeout).",
        initialValue = userSettings.keepScreenOn,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = {
            userSettings.keepScreenOn = it
            KeepScreenOnStateSingleton.setKeepScreenOn(it)
        }
    )
}
