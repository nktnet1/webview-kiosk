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
        infoText = """
            This script injects JavaScript code that will set the
            document.meta.content to 'width=1024', simulating web
            browsing on a Desktop.
            
            This script will also listen to JS history state changes
            (e.g. from Single Page Applications).

            You should only enable this option if setting the user
            agent was insufficient to force Desktop mode, as the
            additional JS here will slow down the page.
        """.trimIndent(),
        initialValue = userSettings.applyDesktopViewport,
        onSave = { userSettings.applyDesktopViewport = it }
    )
}
