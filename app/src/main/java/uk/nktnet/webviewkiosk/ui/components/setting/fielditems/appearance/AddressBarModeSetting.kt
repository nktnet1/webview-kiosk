package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.AddressBarOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun AddressBarModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Address Bar Mode",
        infoText = "Control the visibility of the browser address bar.",
        options = AddressBarOption.entries,
        restricted = userSettings.isRestricted(UserSettingsKeys.Appearance.ADDRESS_BAR_MODE),
        initialValue = userSettings.addressBarMode,
        onSave = { userSettings.addressBarMode = it },
        itemText = {
            when (it) {
                AddressBarOption.HIDDEN -> "Hidden"
                AddressBarOption.HIDDEN_WHEN_LOCKED -> "Hidden when locked"
                AddressBarOption.SHOWN -> "Shown"
            }
        }
    )
}
