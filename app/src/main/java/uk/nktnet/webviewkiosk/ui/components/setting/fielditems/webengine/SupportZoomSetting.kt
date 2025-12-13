package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun SupportZoomSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.WebEngine.SUPPORT_ZOOM

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_engine_support_zoom_title),
        infoText = """
            Sets whether the WebView should support zooming using its on-screen
            zoom controls and gestures.
        """.trimIndent(),
        initialValue = userSettings.supportZoom,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.supportZoom = it }
    )
}
