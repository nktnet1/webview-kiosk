package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.AddressBarActionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.EnumListSettingFieldItem

@Composable
fun AddressBarActionsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    EnumListSettingFieldItem(
        label = "Address Bar Actions",
        infoText = "Reorder and manage visible actions in the address bar.",
        entries = AddressBarActionOption.entries,
        getLabel = { it.label },
        getDefault = { AddressBarActionOption.getDefault() },
        initialValue = userSettings.addressBarActions,
        restricted = userSettings.isRestricted(
            UserSettingsKeys.WebBrowsing.ADDRESS_BAR_ACTIONS
        ),
        onSave = { newList -> userSettings.addressBarActions = newList }
    )
}
