package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun SupportZoomSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Support Zoom",
        infoText = """
            Sets whether the WebView should support zooming using its on-screen
            zoom controls and gestures.
        """.trimIndent(),
        initialValue = userSettings.supportZoom,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.SUPPORT_ZOOM),
        onSave = { userSettings.supportZoom = it }
    )
}
