package com.nktnet.webview_kiosk.ui.components.setting.fields.webcontent

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.EditableSettingItem
import com.nktnet.webview_kiosk.utils.validateUrl

@Composable
fun HomeUrlSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    var value by remember { mutableStateOf(userSettings.homeUrl) }

    EditableSettingItem(
        label = "Home URL",
        infoText = "The URL that can be reset by tapping the screen 10 times in succession, using the floating toolbar icon, the address bar menu.",
        placeholder = "https://example.com",
        initialValue = value,
        isMultiline = false,
        validator = { validateUrl(it) },
        onSave = { newValue ->
            value = newValue
            userSettings.homeUrl = newValue
        }
    )
}
