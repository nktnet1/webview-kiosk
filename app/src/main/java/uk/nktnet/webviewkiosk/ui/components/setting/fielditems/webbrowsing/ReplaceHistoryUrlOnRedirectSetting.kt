package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun ReplaceHistoryUrlOnRedirectSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Replace History URL on Redirect",
        infoText = """
            Set to true to replace the current history entry with the final URL if
            any page redirections occurs, rather than adding multiple entries to the
            history navigation stack.
        """.trimIndent(),
        initialValue = userSettings.replaceHistoryUrlOnRedirect,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.REPLACE_HISTORY_URL_ON_REDIRECT),
        onSave = { userSettings.replaceHistoryUrlOnRedirect = it }
    )
}
