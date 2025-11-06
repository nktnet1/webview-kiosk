package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttKeepAliveSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Keep Alive (seconds)",
        infoText = """
            Maximum interval in seconds allowed between messages sent or
            received by the MQTT client.

            If no message is sent within this interval, the client sends a PING to
            keep the connection alive.
        """.trimIndent(),
        placeholder = "e.g. 60",
        initialValue = userSettings.mqttKeepAlive,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE),
        min = 0,
        max = 65535,
        onSave = { userSettings.mqttKeepAlive = it }
    )
}
