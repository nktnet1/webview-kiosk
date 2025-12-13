package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LoadWithOverviewModeSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_engine_load_with_overview_mode_title),
        infoText = "Load the WebView content fully zoomed out to fit the screen width.",
        initialValue = userSettings.loadWithOverviewMode,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.loadWithOverviewMode = it }
    )
}
