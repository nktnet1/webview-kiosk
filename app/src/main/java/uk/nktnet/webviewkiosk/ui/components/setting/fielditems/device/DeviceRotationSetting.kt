package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.DeviceRotationOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun DeviceRotationSetting(
    deviceRotationState: MutableState<DeviceRotationOption>
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Device Rotation",
        infoText = "Choose a fixed device rotation or select 'Auto' to allow the system to rotate the screen automatically.",
        options = DeviceRotationOption.entries,
        initialValue = deviceRotationState.value,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.DEVICE_ROTATION),
        onSave = {
            deviceRotationState.value = it
            userSettings.deviceRotation = it
        },
        itemText = {
            when (it) {
                DeviceRotationOption.AUTO -> "Auto"
                DeviceRotationOption.ROTATION_0 -> "0°"
                DeviceRotationOption.ROTATION_90 -> "90°"
                DeviceRotationOption.ROTATION_180 -> "180°"
                DeviceRotationOption.ROTATION_270 -> "270°"
            }
        }
    )
}
