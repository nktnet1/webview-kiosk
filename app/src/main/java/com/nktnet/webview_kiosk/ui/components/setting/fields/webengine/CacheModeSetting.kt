package com.nktnet.webview_kiosk.ui.components.setting.fields.webengine

import android.webkit.WebSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.DropdownSettingFieldItem

@Composable
fun CacheModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Cache Mode",
        infoText = "Control how the WebView uses its cache when loading pages.",
        options = listOf(
            WebSettings.LOAD_DEFAULT,
            WebSettings.LOAD_CACHE_ELSE_NETWORK,
            WebSettings.LOAD_NO_CACHE,
            WebSettings.LOAD_CACHE_ONLY
        ),
        initialValue = userSettings.cacheMode,
        onSave = { userSettings.cacheMode = it },
        itemText = {
            when (it) {
                WebSettings.LOAD_DEFAULT -> "Default"
                WebSettings.LOAD_CACHE_ELSE_NETWORK -> "Cache else network"
                WebSettings.LOAD_NO_CACHE -> "No cache"
                WebSettings.LOAD_CACHE_ONLY -> "Cache only"
                else -> "Unknown"
            }
        }
    )
}
