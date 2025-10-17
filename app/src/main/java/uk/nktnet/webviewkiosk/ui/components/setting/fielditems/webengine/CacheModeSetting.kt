package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.CacheModeOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun CacheModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Cache Mode",
        infoText = "Control how the WebView uses its cache when loading pages.",
        options = CacheModeOption.entries,
        initialValue = userSettings.cacheMode,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.CACHE_MODE),
        onSave = { userSettings.cacheMode = it },
        itemText = {
            when (it) {
                CacheModeOption.DEFAULT -> "Default"
                CacheModeOption.CACHE_ELSE_NETWORK -> "Cache else network"
                CacheModeOption.NO_CACHE -> "No cache"
                CacheModeOption.CACHE_ONLY -> "Cache only"
            }
        }
    )
}
