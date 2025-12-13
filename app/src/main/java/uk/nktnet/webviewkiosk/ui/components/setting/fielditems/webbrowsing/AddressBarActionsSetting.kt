package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.AddressBarActionOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.EnumListSettingFieldItem

@Composable
fun AddressBarActionsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.ADDRESS_BAR_ACTIONS

    EnumListSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_address_bar_actions_title),
        infoText = """
            Manage visible actions in the address bar.

            Use the drag handle at the end to reorder the items.
        """.trimIndent(),
        entries = AddressBarActionOption.entries,
        getLabel = { it.label },
        getDefault = { AddressBarActionOption.getDefault() },
        initialValue = userSettings.addressBarActions,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { newList -> userSettings.addressBarActions = newList }
    )
}
