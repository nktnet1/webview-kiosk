package com.nktnet.webview_kiosk.ui.components.setting.fields.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.BooleanSettingFieldItem

@Composable
fun AllowGoHomeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Go Home",
        infoText = "Whether the user can return to the configured home page when tapping the screen 10 times in quick succession.",
        initialValue = userSettings.allowGoHome,
        onSave = { userSettings.allowGoHome = it }
    )
}
