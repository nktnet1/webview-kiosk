package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.ImmersiveModeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun ImmersiveModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.IMMERSIVE_MODE

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.appearance_immersive_mode_title),
        infoText = """
            In immersive mode, your system bars (status and navigation) are hidden.

            You can temporarily reveal the system bars with gestures such as swiping
            from the edge of the screen where the bar is hidden from.

            Note: immersive mode is enabled automatically when entering fullscreen
            (for example, when watching a video), irrespective of this setting.
        """.trimIndent(),
        options = ImmersiveModeOption.entries,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.immersiveMode,
        onSave = { userSettings.immersiveMode = it },
        itemText = { it.label },
    )
}
