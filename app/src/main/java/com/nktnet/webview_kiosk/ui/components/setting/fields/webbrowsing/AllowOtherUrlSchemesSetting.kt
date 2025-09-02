package com.nktnet.webview_kiosk.ui.components.setting.fields.webbrowsing

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.BooleanSettingFieldItem

@Composable
fun AllowOtherUrlSchemesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    var value by remember { mutableStateOf(userSettings.allowOtherUrlSchemes) }

    BooleanSettingFieldItem(
        label = "Allow Other URL Schemes",
        infoText = "Allow opening of non-http/https URL schemes such as 'mailto:', 'sms:', 'tel:', 'intent:', 'spotify:', 'whatsapp:', etc in other apps.\n\nNOTE: This only works when in unlocked/unpinned mode.",
        initialValue = value,
        onSave = { newValue ->
            value = newValue
            userSettings.allowOtherUrlSchemes = newValue
        }
    )
}
