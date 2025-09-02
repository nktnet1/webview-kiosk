package com.nktnet.webview_kiosk.ui.components.setting.fields.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.BooleanSettingFieldItem

@Composable
fun AllowRefreshSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Refresh",
        infoText = "Whether the user can pull down at the top of a webpage to refresh.",
        initialValue = userSettings.allowRefresh,
        onSave = { userSettings.allowRefresh = it }
    )
}
