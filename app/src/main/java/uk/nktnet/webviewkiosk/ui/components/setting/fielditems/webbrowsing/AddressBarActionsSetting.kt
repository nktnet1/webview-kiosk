package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.AddressBarActionOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.EnumListSettingFieldItem

@Composable
fun AddressBarActionsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    EnumListSettingFieldItem(
        label = "Address Bar Actions",
        infoText = """
            Manage visible actions in the address bar.

            Use the drag handle at the end to reorder the items.
        """.trimIndent(),
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
