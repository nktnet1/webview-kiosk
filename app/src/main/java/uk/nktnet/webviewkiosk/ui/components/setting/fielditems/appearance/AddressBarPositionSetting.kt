package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.AddressBarPositionOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun AddressBarPositionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.ADDRESS_BAR_POSITION

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.appearance_address_bar_position_title),
        infoText = """
            Customise the position of the address bar - either top or bottom.
        """.trimIndent(),
        options = AddressBarPositionOption.entries,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.addressBarPosition,
        onSave = { userSettings.addressBarPosition = it },
        itemText = { it.label },
    )
}
