package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.initMqttForegroundService

@Composable
fun MqttUseForegroundServiceSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.USE_FOREGROUND_SERVICE

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_use_foreground_service_title),
        infoText = """
            When enabled, a foreground service will be started to keep the MQTT
            connection alive.

            This will maintain the connection even when ${Constants.APP_NAME} goes
            to the background or the device screen is turned off.
        """.trimIndent(),
        initialValue = userSettings.mqttUseForegroundService,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { isEnabled ->
            val isChanged = isEnabled != userSettings.mqttUseForegroundService
            if (isChanged) {
                userSettings.mqttUseForegroundService = isEnabled
                initMqttForegroundService(context, isEnabled)
            }
        }
    )
}
