package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun UseWideViewPortSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Use Wide ViewPort",
        infoText = "Enable wide viewport support in the WebView for responsive pages.",
        initialValue = userSettings.useWideViewPort,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT),
        onSave = { userSettings.useWideViewPort = it }
    )
}
