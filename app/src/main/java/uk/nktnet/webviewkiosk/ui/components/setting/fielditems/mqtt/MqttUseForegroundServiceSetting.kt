package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
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
            Start a Foreground Service to keep the MQTT connection alive.

            This will maintain the connection even when ${stringResource(R.string.app_name)}
            goes to the background (e.g. another app is opened) or the device screen is
            turned off (by using PowerManager.PARTIAL_WAKE_LOCK).

            Turn on notifications to see the current MQTT status being updated
            by the foreground service every second.

            Also, It is highly recommended that you disable battery optimisation
            for ${stringResource(R.string.app_name)} to stop the service from
            being killed.

            For more information, visit:
            - https://dontkillmyapp.com
        """.trimIndent(),
        initialValue = userSettings.mqttUseForegroundService,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { isEnabled ->
            val isChanged = isEnabled != userSettings.mqttUseForegroundService
            if (isChanged) {
                userSettings.mqttUseForegroundService = isEnabled
                initMqttForegroundService(
                    context,
                    userSettings.mqttEnabled && isEnabled,
                )
            }
        }
    )
}
