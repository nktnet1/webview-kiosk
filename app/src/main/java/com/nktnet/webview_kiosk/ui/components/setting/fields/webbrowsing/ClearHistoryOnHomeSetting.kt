package com.nktnet.webview_kiosk.ui.components.setting.fields.webbrowsing

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.BooleanSettingFieldItem

@Composable
fun ClearHistoryOnHomeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    var value by remember { mutableStateOf(userSettings.clearHistoryOnHome) }

    BooleanSettingFieldItem(
        label = "Clear History on Home",
        infoText = "Clear the browser history whenever the user triggers an action to return home.",
        initialValue = value,
        onSave = { newValue ->
            value = newValue
            userSettings.clearHistoryOnHome = newValue
        }
    )
}
