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
import android.content.Intent
import uk.nktnet.webviewkiosk.managers.MqttForegroundService

@Composable
fun MqttUseForegroundServiceSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.USE_FOREGROUND_SERVICE

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_use_foreground_service_title),
        infoText = """
            When enabled, ${Constants.APP_NAME} will run the MQTT connection
            as a foreground service,
            
            This will keep the connection alive even when the application goes
            to the background or the screen is turned off.
        """.trimIndent(),
        initialValue = userSettings.mqttUseForegroundService,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { isEnabled ->
            val isChanged = isEnabled != userSettings.mqttUseForegroundService
            if (isChanged) {
                userSettings.mqttUseForegroundService = isEnabled
                if (isEnabled) {
                    context.startService(
                        Intent(context, MqttForegroundService::class.java)
                    )
                } else {
                    context.stopService(
                        Intent(context, MqttForegroundService::class.java)
                    )
                }
            }
        }
    )
}
