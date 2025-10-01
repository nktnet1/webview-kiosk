package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun UseWideViewPortSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Use Wide ViewPort",
        infoText = "Enable wide viewport support in the WebView for responsive pages.",
        initialValue = userSettings.useWideViewPort,
        onSave = { userSettings.useWideViewPort = it }
    )
}
