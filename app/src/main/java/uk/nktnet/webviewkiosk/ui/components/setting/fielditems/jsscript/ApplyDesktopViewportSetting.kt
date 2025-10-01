package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun ApplyDesktopViewportSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Apply Desktop Viewport",
        infoText = "Whether to force a desktop-style viewport on websites for consistent zoom and layout behavior.",
        initialValue = userSettings.applyDesktopViewport,
        onSave = { userSettings.applyDesktopViewport = it }
    )
}
