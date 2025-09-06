package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowBookmarkAccessSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Bookmark Access",
        infoText = "Whether the user can access saved bookmarks from the address bar.",
        initialValue = userSettings.allowBookmarkAccess,
        onSave = { userSettings.allowBookmarkAccess = it }
    )
}
