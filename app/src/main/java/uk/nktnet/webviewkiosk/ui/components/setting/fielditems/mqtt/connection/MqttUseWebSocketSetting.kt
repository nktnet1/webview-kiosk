package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttUseWebSocketSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Use WebSocket",
        infoText = """
            When enabled, the client will use WebSocket transport
            instead of TCP.

            Ensure the broker supports WebSocket on the selected port,
            which is typically
            - 80 for WS
            - 443 for WSS (WebSocket Secure)

            You should only enable WebSocket when strictly necessary, as it
            will consume more resources. A valid use case would be when you
            are connected to a network with a firewall that blocks non-standard
            ports like 8883.
        """.trimIndent(),
        initialValue = userSettings.mqttUseWebSocket,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.USE_WEBSOCKET),
        onSave = { userSettings.mqttUseWebSocket = it }
    )
}
