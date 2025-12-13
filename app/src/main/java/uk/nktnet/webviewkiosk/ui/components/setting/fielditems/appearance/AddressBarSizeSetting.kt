package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.AddressBarSizeOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun AddressBarSizeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.ADDRESS_BAR_SIZE

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.appearance_address_bar_size_title),
        infoText = """
            Customise the size of the address bar, which will determine the
            height, font size, padding and icon size.
        """.trimIndent(),
        options = AddressBarSizeOption.entries,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.addressBarSize,
        onSave = { userSettings.addressBarSize = it },
        itemText = { it.label },
    )
}
