package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableZoomSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable Zoom",
        infoText = "Allow pinch-to-zoom and zoom controls in the WebView.",
        initialValue = userSettings.enableZoom,
        onSave = { userSettings.enableZoom = it }
    )
}
