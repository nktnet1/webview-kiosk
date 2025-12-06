package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun SupportZoomSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Support Zoom",
        infoText = """
            Sets whether the WebView should support zooming using its on-screen
            zoom controls and gestures.
        """.trimIndent(),
        initialValue = userSettings.supportZoom,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.SUPPORT_ZOOM),
        onSave = { userSettings.supportZoom = it }
    )
}
