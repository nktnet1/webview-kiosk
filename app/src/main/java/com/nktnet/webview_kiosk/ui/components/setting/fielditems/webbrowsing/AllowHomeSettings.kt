package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowGoHomeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Go Home",
        infoText = "Whether the user can return to the configured home page when tapping the top-left quadrant of the screen 10 times in quick succession (300ms interval).",
        initialValue = userSettings.allowGoHome,
        onSave = { userSettings.allowGoHome = it }
    )
}
