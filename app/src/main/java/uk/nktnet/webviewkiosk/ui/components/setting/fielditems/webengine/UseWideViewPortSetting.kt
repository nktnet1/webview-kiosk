package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun UseWideViewPortSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_engine_use_wide_viewport_title),
        infoText = "Enable wide viewport support in the WebView for responsive pages.",
        initialValue = userSettings.useWideViewport,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.useWideViewport = it }
    )
}
