package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun ApplyDesktopViewportWidthSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Apply Desktop Viewport Width (px)",
        infoText = """
            This script injects JavaScript code that sets
            document.meta.content to 'width=YOUR_WIDTH_VALUE',
            simulating web browsing on a Desktop.

            JS history state changes will also be subscribed to
            (e.g. from Single Page Applications), and the script
            will be re-triggered as needed.

            You should only enable this option if setting the user
            agent was insufficient to force Desktop mode, as the
            additional JS here will slow down the page.

            You may also want to enable the following options under
            Settings -> Web Engine:
              - User Agent: Desktop
              - Use Wide Viewport: True
              - Load with Overview Mode: True
              - Enable Zoom: True

            The minimum possible value is ${Constants.MIN_DESKTOP_WIDTH}.

            To disable, use the value 0.
        """.trimIndent(),
        initialValue = userSettings.applyDesktopViewportWidth,
        min = Constants.MIN_DESKTOP_WIDTH,
        placeholder = "e.g. 1024 (or 0 to disable)",
        onSave = { userSettings.applyDesktopViewportWidth = it }
    )
}
