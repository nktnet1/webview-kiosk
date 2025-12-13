package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun ClearHistoryOnHomeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_clear_history_on_home_title),
        infoText = "Clear the browser history whenever the user triggers an action to return home.",
        initialValue = userSettings.clearHistoryOnHome,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.clearHistoryOnHome = it }
    )
}
