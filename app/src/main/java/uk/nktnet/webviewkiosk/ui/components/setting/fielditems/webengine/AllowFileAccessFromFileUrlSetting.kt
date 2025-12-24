package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowFileAccessFromFileURLsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_engine_allow_file_access_from_file_urls_title),
        infoText = """
            This method was deprecated in API level 30 (Android 11).

            Sets whether cross-origin requests in the context of a file scheme URL
            should be allowed to access content from other file scheme URLs. Note
            that some accesses such as image HTML elements don't follow same-origin
            rules and aren't affected by this setting.

            Don't enable this setting if you open files that may be created or altered
            by external sources. Enabling this setting allows malicious scripts loaded
            in a file:// context to access arbitrary local files including WebView
            cookies and app private data.
        """.trimIndent(),
        initialValue = userSettings.allowFileAccessFromFileURLs,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowFileAccessFromFileURLs = it }
    )
}
