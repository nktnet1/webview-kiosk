package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun DisplayZoomControlsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Display Zoom Controls",
        infoText = "Show zoom in/out buttons on the WebView when zoom is enabled.",
        initialValue = userSettings.displayZoomControls,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS),
        onSave = { userSettings.displayZoomControls = it }
    )
}
