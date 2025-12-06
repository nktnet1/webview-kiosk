package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun RequestFocusOnPageStartSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Request Focus on Page Start",
        infoText = """
            Sets whether the WebView should request focus when a page starts loading.
        """.trimIndent(),
        initialValue = userSettings.requestFocusOnPageStart,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.REQUEST_FOCUS_ON_PAGE_START),
        onSave = { userSettings.requestFocusOnPageStart = it }
    )
}
