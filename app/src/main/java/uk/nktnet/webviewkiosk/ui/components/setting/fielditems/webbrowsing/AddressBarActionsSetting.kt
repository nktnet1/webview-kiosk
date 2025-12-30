package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.WebviewControlActionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.EnumListSettingFieldItem

@Composable
fun AddressBarActionsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.ADDRESS_BAR_ACTIONS

    EnumListSettingFieldItem(
        label = stringResource(R.string.web_browsing_address_bar_actions_title),
        infoText = """
            Manage visible actions in the address bar.

            Use the drag handle at the end to reorder the items.
        """.trimIndent(),
        entries = WebviewControlActionOption.entries,
        getLabel = { it.label },
        getDefault = { WebviewControlActionOption.getDefaultAddressBarOptions() },
        initialValue = userSettings.addressBarActions,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { newList -> userSettings.addressBarActions = newList }
    )
}
