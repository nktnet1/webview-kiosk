package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableBrightnessApiSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable Brightness API",
        infoText = """
            Allow web pages to get or set screen brightness through a JavaScript
            interface. Web content can use:

            window.WebviewKioskBrightnessInterface.getBrightness()
            window.WebviewKioskBrightnessInterface.setBrightness(value)

            to read or change the current screen brightness percentage.
        """.trimIndent(),
        initialValue = userSettings.enableBrightnessApi,
        restricted = userSettings.isRestricted(UserSettingsKeys.JsScripts.ENABLE_BRIGHTNESS_API),
        onSave = { userSettings.enableBrightnessApi = it }
    )
}
