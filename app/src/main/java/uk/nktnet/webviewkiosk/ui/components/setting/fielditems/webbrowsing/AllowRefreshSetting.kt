package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowRefreshSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Refresh",
        infoText = """
            Set to true to allow the user to refresh the page, e.g. using the
            - address bar actions
            - kiosk control panel
            - pull to refresh (can be configured separately)
        """.trimIndent(),
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.ALLOW_REFRESH),
        initialValue = userSettings.allowRefresh,
        onSave = { userSettings.allowRefresh = it }
    )
}
