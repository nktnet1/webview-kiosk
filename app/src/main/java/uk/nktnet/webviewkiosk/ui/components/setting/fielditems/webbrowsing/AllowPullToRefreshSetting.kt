package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowPullToRefreshSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Pull to Refresh",
        infoText = """
            Set to true to allow the user to refresh the page by pulling down
            from the top 1/4 of the screen.

            Note that this requires:
            - the "Allow Refresh" setting to also be true
            - the page to have been scrolled fully to the top prior to the gesture
            - a single finger (touch) is used
        """.trimIndent(),
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.ALLOW_PULL_TO_REFRESH),
        initialValue = userSettings.allowPullToRefresh,
        onSave = { userSettings.allowPullToRefresh = it }
    )
}
