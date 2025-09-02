package com.nktnet.webview_kiosk.ui.components.setting.fields.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.BooleanSettingFieldItem

@Composable
fun AcceptCookiesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Accept Cookies",
        infoText = "Allow websites to store and read cookies.",
        initialValue = userSettings.acceptCookies,
        onSave = { userSettings.acceptCookies = it }
    )
}
