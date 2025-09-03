package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem
import com.nktnet.webview_kiosk.utils.validateUrl

@Composable
fun SearchProviderUrlSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "Search Provider URL",
        infoText = "The URL used for search queries in the address bar. This URL must include a query parameter, e.g.\n    ${Constants.DEFAULT_SEARCH_PROVIDER_URL}",
        placeholder = Constants.DEFAULT_SEARCH_PROVIDER_URL,
        initialValue = userSettings.searchProviderUrl,
        isMultiline = false,
        validator = { validateUrl(it) },
        onSave = { userSettings.searchProviderUrl = it }
    )
}
