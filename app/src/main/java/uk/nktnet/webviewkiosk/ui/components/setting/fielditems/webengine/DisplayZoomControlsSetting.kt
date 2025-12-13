package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun DisplayZoomControlsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_engine_display_zoom_controls_title),
        infoText = """
            Sets whether the WebView should display on-screen zoom controls
            when using the built-in zoom mechanisms.
        """.trimIndent(),
        initialValue = userSettings.displayZoomControls,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.displayZoomControls = it }
    )
}
