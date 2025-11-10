package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

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
        infoText = "Allow web pages to access device battery status through a JavaScript interface. " +
            "Web content can use window.getBatteryStatus() to retrieve battery level, charging status, " +
            "temperature, and other battery information.",
        initialValue = userSettings.enableBatteryApi,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.ENABLE_BATTERY_API),
        onSave = { userSettings.enableBatteryApi = it }
    )
}
