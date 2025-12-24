package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.AddressBarModeOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun AddressBarModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.ADDRESS_BAR_MODE

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.appearance_address_bar_mode_title),
        infoText = "Customise the visibility of the address bar.",
        options = AddressBarModeOption.entries,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.addressBarMode,
        onSave = { userSettings.addressBarMode = it },
        itemText = { it.label },
    )
}
