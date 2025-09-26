package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun BlockedMessageSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "Blocked Message",
        infoText = "Custom message shown on blocked pages. Can be left empty.",
        placeholder = "e.g. This site is blocked by <Company Name>",
        initialValue = userSettings.blockedMessage,
        isMultiline = true,
        validator = { true },
        onSave = { userSettings.blockedMessage = it }
    )
}
