package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.DeviceRotationOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem
import com.nktnet.webview_kiosk.utils.setDeviceRotation

@Composable
fun DeviceRotationSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.DEVICE_ROTATION

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.device_rotation_title),
        infoText = """
            Choose a fixed device rotation or select 'Auto' to allow
            the system to rotate the screen automatically.
        """.trimIndent(),
        options = DeviceRotationOption.entries,
        initialValue = userSettings.rotation,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = {
            userSettings.rotation = it
            setDeviceRotation(context, it)
        },
        itemText = { it.label },
    )
}
