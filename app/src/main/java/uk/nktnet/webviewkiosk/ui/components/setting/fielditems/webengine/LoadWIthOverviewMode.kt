package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LoadWithOverviewModeSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Load With Overview Mode",
        infoText = "Load the WebView content fully zoomed out to fit the screen width.",
        initialValue = userSettings.loadWithOverviewMode,
        onSave = { userSettings.loadWithOverviewMode = it }
    )
}
