package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.AddressBarPositionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun AddressBarPositionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Address Bar Position",
        infoText = "Choose whether the address bar appears at the top or bottom of the screen.",
        options = AddressBarPositionOption.entries,
        restricted = userSettings.isRestricted(UserSettingsKeys.Appearance.ADDRESS_BAR_POSITION),
        initialValue = userSettings.addressBarPosition,
        onSave = { userSettings.addressBarPosition = it },
        itemText = { it.label },
    )
}
