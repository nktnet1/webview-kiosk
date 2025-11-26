package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.mqtt.MqttManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttEnabledSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    BooleanSettingFieldItem(
        label = "Enabled",
        infoText = """
            When enabled, ${Constants.APP_NAME} will connect to your configured
            MQTT broker and subscribe/publish to the defined topics.

            This will take effect for new app launches.

            If you are currently in a disconnected state, you will need to manually click
            the "Connect" button after enabling this option (or simply restarts the app).
        """.trimIndent(),
        initialValue = userSettings.mqttEnabled,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.ENABLED),
        onSave = { isEnabled ->
            val isChanged = isEnabled != userSettings.mqttEnabled
            if (isChanged) {
                userSettings.mqttEnabled = isEnabled
                if (!isEnabled && MqttManager.isConnectedOrReconnect()) {
                    MqttManager.disconnect()
                    MqttManager.updateConfig(systemSettings, userSettings, false)
                }
            }
        }
    )
}
