package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowBookmarkAccessSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_allow_bookmark_access_title),
        infoText = "Whether the user can access saved bookmarks from the address bar.",
        initialValue = userSettings.allowBookmarkAccess,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowBookmarkAccess = it }
    )
}
