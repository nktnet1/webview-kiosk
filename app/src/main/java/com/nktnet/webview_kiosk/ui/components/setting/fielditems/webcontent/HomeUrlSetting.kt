package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem
import com.nktnet.webview_kiosk.utils.validateUrl

@Composable
fun HomeUrlSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "Home URL",
        infoText = "The URL that can be reset by tapping the screen 10 times in succession, using the floating toolbar icon, the address bar menu.",
        placeholder = "https://example.com",
        initialValue = userSettings.homeUrl,
        isMultiline = false,
        validator = { validateUrl(it) },
        onSave = { userSettings.homeUrl = it }
    )
}
