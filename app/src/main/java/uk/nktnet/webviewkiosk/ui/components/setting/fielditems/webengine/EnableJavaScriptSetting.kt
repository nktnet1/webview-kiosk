package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableJavaScriptSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable JavaScript",
        infoText = "Allow the execution of JavaScript in web pages.",
        initialValue = userSettings.enableJavaScript,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.ENABLE_JAVASCRIPT),
        onSave = { userSettings.enableJavaScript = it }
    )
}
