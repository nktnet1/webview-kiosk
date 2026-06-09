package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttServerPortSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.SERVER_PORT

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_server_port_title),
        infoText = """
            The TCP port of the MQTT broker the app should connect to.

            Typically,
            - 1883 - MQTT (TCP)
            - 8883 - MQTTS (TCP with SSL/TLS)
            - 80 - WS (WebSocket, same port as HTTP)
            - 443 - WSS (WebSocket Secure, same port as HTTPS)
        """.trimIndent(),
        initialValue = userSettings.mqttServerPort,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 1,
        max = 65535,
        placeholder = "e.g. 1883",
        onSave = { userSettings.mqttServerPort = it },
        extraContent = { v, _ ->
            if (v.toIntOrNull() in setOf(80, 443)) {
                Row(
                    modifier = Modifier.padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_info_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = """
                            Consider enabling the setting:
                            MQTT > Connection -> Use WebSocket
                        """.trimIndent(),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    )
}
