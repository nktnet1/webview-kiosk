package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableBatteryApiSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable Battery API",
        infoText = """
            Allow web pages to access device battery status through a JavaScript
            interface. Web content can use

              const data = JSON.parse(
                window.WebviewKioskBatteryInterface.getBatteryStatus()
              )
            
            to retrieve battery level, charging status, temperature and other
            battery information.
        """.trimIndent(),
        initialValue = userSettings.enableBatteryApi,
        restricted = userSettings.isRestricted(UserSettingsKeys.JsScripts.ENABLE_BATTERY_API),
        onSave = { userSettings.enableBatteryApi = it }
    )
}
