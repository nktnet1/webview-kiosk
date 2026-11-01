package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.FloatingToolbarModeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun FloatingToolbarModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE

    DropdownSettingFieldItem(
        label = stringResource(R.string.appearance_floating_toolbar_mode_title),
        infoText = stringResource(R.string.setting_info_floating_toolbar_mode),
        options = FloatingToolbarModeOption.entries,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.floatingToolbarMode,
        onSave = { userSettings.floatingToolbarMode = it },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
