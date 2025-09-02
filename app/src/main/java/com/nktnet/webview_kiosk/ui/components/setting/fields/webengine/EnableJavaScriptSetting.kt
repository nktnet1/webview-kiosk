package com.nktnet.webview_kiosk.ui.components.setting.fields.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.BooleanSettingFieldItem

@Composable
fun EnableJavaScriptSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable JavaScript",
        infoText = "Allow the execution of JavaScript in web pages.",
        initialValue = userSettings.enableJavaScript,
        onSave = { userSettings.enableJavaScript = it }
    )
}
