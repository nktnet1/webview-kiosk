package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableZoomSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable Zoom",
        infoText = "Allow pinch-to-zoom and zoom controls in the WebView.",
        initialValue = userSettings.enableZoom,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.ENABLE_ZOOM),
        onSave = { userSettings.enableZoom = it }
    )
}
