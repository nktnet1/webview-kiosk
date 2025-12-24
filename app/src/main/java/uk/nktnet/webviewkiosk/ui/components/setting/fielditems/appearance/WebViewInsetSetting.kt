package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.WebViewInsetOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun WebViewInsetSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.WEBVIEW_INSET

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.appearance_webview_inset_title),
        infoText = "Select which WindowInsets the WebView should respect for padding.",
        options = WebViewInsetOption.entries,
        initialValue = userSettings.webViewInset,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.webViewInset = it },
        itemText = { it.label },
    )
}
