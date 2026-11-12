package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun BlockVolumeKeysSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.BLOCK_VOLUME_KEYS

    BooleanSettingFieldItem(
        initialValue = userSettings.blockVolumeKeys,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.blockVolumeKeys = it },
    )
}
