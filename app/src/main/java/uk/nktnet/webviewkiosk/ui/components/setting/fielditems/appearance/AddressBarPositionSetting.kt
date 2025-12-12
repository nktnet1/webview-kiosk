package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.AddressBarPositionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun AddressBarPositionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.appearance_address_bar_position_title),
        infoText = """
            Customise the position of the address bar - either top or bottom.
        """.trimIndent(),
        options = AddressBarPositionOption.entries,
        restricted = userSettings.isRestricted(UserSettingsKeys.Appearance.ADDRESS_BAR_POSITION),
        initialValue = userSettings.addressBarPosition,
        onSave = { userSettings.addressBarPosition = it },
        itemText = { it.label },
    )
}
