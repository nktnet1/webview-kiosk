package com.nktnet.webview_kiosk.ui.components.setting.fields.webbrowsing

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.TextSettingFieldItem

@Composable
fun SearchProviderUrlSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    var value by remember { mutableStateOf(userSettings.searchProviderUrl) }

    fun validate(input: String): Boolean {
        return input.contains("{query}") || input.contains("q=")
    }

    TextSettingFieldItem(
        label = "Search Provider URL",
        infoText = "The URL used for search queries in the address bar. This URL must include a query parameter, e.g.\n    https://google.com/search?q=",
        placeholder = "https://search.example.com/?q={query}",
        initialValue = value,
        isMultiline = false,
        validator = { validate(it) },
        onSave = { newValue ->
            value = newValue
            userSettings.searchProviderUrl = newValue
        }
    )
}
