package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.utils.setDeviceRotation

@Composable
fun DeviceRotationSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.DEVICE_ROTATION

    DropdownSettingFieldItem(
        label = stringResource(R.string.device_rotation_title),
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
