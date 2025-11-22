package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun BuiltInZoomControlsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Built In Zoom Controls",
        infoText = """
           Sets whether the WebView should use its built-in zoom mechanisms.

           The built-in zoom mechanisms comprise on-screen zoom controls, which are displayed
           over the WebView's content, and the use of a pinch gesture to control zooming.
        """.trimIndent(),
        initialValue = userSettings.builtInZoomControls,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS),
        onSave = { userSettings.builtInZoomControls = it }
    )
}
