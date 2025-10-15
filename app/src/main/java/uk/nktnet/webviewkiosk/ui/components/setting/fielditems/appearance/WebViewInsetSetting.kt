package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.WebViewInset
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun WebViewInsetSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "WebView Insets",
        infoText = "Select which WindowInsets the WebView should respect for padding.",
        options = WebViewInset.entries,
        initialValue = userSettings.webViewInset,
        restricted = userSettings.isRestricted(UserSettingsKeys.Appearance.WEBVIEW_INSET),
        onSave = { userSettings.webViewInset = it },
        itemText = { it.label }
    )
}
