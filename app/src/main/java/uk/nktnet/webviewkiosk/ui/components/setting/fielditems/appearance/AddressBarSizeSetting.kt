package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.AddressBarSizeOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

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
