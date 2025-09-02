package com.nktnet.webview_kiosk.ui.components.setting.fields.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.BooleanSettingFieldItem

@Composable
fun AllowBackwardsNavigationSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Backwards Navigation",
        infoText = "Whether the user can use the device 'back' button to go back one page in history.",
        initialValue = userSettings.allowBackwardsNavigation,
        onSave = { userSettings.allowBackwardsNavigation = it }
    )
}
