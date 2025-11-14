package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun InitialScaleSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Initial Scale",
        infoText = """
            Sets the initial scale for this WebView as a percentage. 0 means default.

            The behavior for the default scale depends on the state of
            - useWideViewPort
            - loadWithOverviewMode

            If the content fits into the WebView control by width, the zoom is set to 100%.

            For wide content, the behaviour depends on the state of loadWithOverviewMode.
            If its value is true, the content will be zoomed out to be fit by width into
            the WebView control, otherwise not.

            If initial scale is greater than 0, WebView starts with this value as initial
            scale. Please note that unlike the scale properties in the viewport meta tag,
            this method doesn't take the screen density into account.
        """.trimIndent(),
        placeholder = "e.g. 100",
        initialValue = userSettings.initialScale,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.INITIAL_SCALE),
        min = 0,
        onSave = { userSettings.initialScale = it }
    )
}
