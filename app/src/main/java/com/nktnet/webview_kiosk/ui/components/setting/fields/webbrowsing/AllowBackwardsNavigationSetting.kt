package com.nktnet.webview_kiosk.ui.components.setting.fields.webbrowsing

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.BooleanSettingFieldItem

@Composable
fun AllowBackwardsNavigationSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    var value by remember { mutableStateOf(userSettings.allowBackwardsNavigation) }

    BooleanSettingFieldItem(
        label = "Allow Backwards Navigation",
        infoText = "Whether the user can use the device 'back' button to go back one page in history.",
        initialValue = value,
        onSave = { newValue ->
            value = newValue
            userSettings.allowBackwardsNavigation = newValue
        }
    )
}
