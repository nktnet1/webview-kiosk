package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.AddressBarSizeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun AddressBarSizeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Address Bar Size",
        infoText = """
            Customise the size of the address bar, which will determine the
            height, font size, padding and icon size.
        """.trimIndent(),
        options = AddressBarSizeOption.entries,
        restricted = userSettings.isRestricted(UserSettingsKeys.Appearance.ADDRESS_BAR_SIZE),
        initialValue = userSettings.addressBarSize,
        onSave = { userSettings.addressBarSize = it },
        itemText = { it.label },
    )
}
