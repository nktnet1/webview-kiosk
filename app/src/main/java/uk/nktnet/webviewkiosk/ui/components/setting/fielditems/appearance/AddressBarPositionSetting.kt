package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.AddressBarPositionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun AddressBarPositionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.ADDRESS_BAR_POSITION

    DropdownSettingFieldItem(
        options = AddressBarPositionOption.entries,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.addressBarPosition,
        onSave = { userSettings.addressBarPosition = it },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
