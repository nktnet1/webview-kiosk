package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun DisplayZoomControlsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Display Zoom Controls",
        infoText = "Show zoom in/out buttons on the WebView when zoom is enabled.",
        initialValue = userSettings.displayZoomControls,
        onSave = { userSettings.displayZoomControls = it }
    )
}
