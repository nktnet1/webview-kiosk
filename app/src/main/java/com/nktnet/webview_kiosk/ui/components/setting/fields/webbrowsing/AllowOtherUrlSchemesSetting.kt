package com.nktnet.webview_kiosk.ui.components.setting.fields.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.BooleanSettingFieldItem

@Composable
fun AllowOtherUrlSchemesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Other URL Schemes",
        infoText = "Allow opening of non-http/https URL schemes such as 'mailto:', 'sms:', 'tel:', 'intent:', 'spotify:', 'whatsapp:', etc in other apps.\n\nNOTE: This only works when in unlocked/unpinned mode.",
        initialValue = userSettings.allowOtherUrlSchemes,
        onSave = { userSettings.allowOtherUrlSchemes = it }
    )
}
