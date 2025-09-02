package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AcceptThirdPartyCookiesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Accept Third-party Cookies",
        infoText = "Allow third-party websites to set cookies in this WebView.",
        initialValue = userSettings.acceptThirdPartyCookies,
        onSave = { userSettings.acceptThirdPartyCookies = it }
    )
}
