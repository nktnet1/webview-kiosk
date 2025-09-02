package com.nktnet.webview_kiosk.ui.components.setting.fields.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.AddressBarOption
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.DropdownSettingFieldItem

@Composable
fun AddressBarModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Address Bar Mode",
        infoText = "Control the visibility of the browser address bar.",
        options = AddressBarOption.entries,
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
