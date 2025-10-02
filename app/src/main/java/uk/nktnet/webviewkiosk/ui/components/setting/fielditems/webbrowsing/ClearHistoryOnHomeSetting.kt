package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun ClearHistoryOnHomeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Clear History on Home",
        infoText = "Clear the browser history whenever the user triggers an action to return home.",
        initialValue = userSettings.clearHistoryOnHome,
        onSave = { userSettings.clearHistoryOnHome = it }
    )
}
