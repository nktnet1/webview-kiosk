package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LoadWithOverviewModeSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Load With Overview Mode",
        infoText = "Load the WebView content fully zoomed out to fit the screen width.",
        initialValue = userSettings.loadWithOverviewMode,
        onSave = { userSettings.loadWithOverviewMode = it }
    )
}
