package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun BuiltInZoomControlsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_engine_built_in_zoom_controls_title),
        infoText = """
           Sets whether the WebView should use its built-in zoom mechanisms.

           The built-in zoom mechanisms comprise on-screen zoom controls, which are displayed
           over the WebView's content, and the use of a pinch gesture to control zooming.
        """.trimIndent(),
        initialValue = userSettings.builtInZoomControls,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS),
        onSave = { userSettings.builtInZoomControls = it }
    )
}
