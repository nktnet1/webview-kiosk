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

    BooleanSettingFieldItem(
        label = "Block Volume Keys",
        infoText = """
            Prevent users from changing the device volume using hardware keys while in the kiosk app.
        """.trimIndent(),
        initialValue = userSettings.blockVolumeKeys,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.BLOCK_VOLUME_KEYS),
        onSave = { userSettings.blockVolumeKeys = it }
    )
}
