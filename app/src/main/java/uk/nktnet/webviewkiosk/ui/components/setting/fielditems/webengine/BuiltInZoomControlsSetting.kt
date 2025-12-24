package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun BuiltInZoomControlsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_engine_built_in_zoom_controls_title),
        infoText = """
           Sets whether the WebView should use its built-in zoom mechanisms.

           The built-in zoom mechanisms comprise on-screen zoom controls, which are displayed
           over the WebView's content, and the use of a pinch gesture to control zooming.
        """.trimIndent(),
        initialValue = userSettings.builtInZoomControls,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.builtInZoomControls = it }
    )
}
