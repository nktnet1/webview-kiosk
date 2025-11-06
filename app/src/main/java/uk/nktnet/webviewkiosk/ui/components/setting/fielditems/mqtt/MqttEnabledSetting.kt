package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.mqtt.MqttManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttEnabledSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Enabled",
        infoText = """
            Enable or disable MQTT connectivity for this device.

            When enabled, the app will connect to the configured
            MQTT broker and subscribe/publish to the defined topics.
        """.trimIndent(),
        initialValue = userSettings.mqttEnabled,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.ENABLED),
        onSave = { isEnabled ->
            val isChanged = isEnabled != userSettings.mqttEnabled
            if (isChanged) {
                userSettings.mqttEnabled = isEnabled
                if (isEnabled) {
                    MqttManager.connect(userSettings)
                } else {
                    MqttManager.disconnect()
                }
            }
        }
    )
}
