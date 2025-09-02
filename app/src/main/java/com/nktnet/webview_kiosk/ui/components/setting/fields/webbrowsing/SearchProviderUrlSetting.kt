package com.nktnet.webview_kiosk.ui.components.setting.fields.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.TextSettingFieldItem

@Composable
fun SearchProviderUrlSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "Search Provider URL",
        infoText = "The URL used for search queries in the address bar. This URL must include a query parameter, e.g.\n    https://google.com/search?q=",
        placeholder = "https://search.example.com/?q={query}",
        initialValue = userSettings.searchProviderUrl,
        isMultiline = false,
        validator = { it.contains("{query}") || it.contains("q=") },
        onSave = { userSettings.searchProviderUrl = it }
    )
}
