package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttWebSocketServerPathSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.WEBSOCKET_SERVER_PATH)

    TextSettingFieldItem(
        label = "WebSocket Server Path",
        infoText = """
            The path the MQTT broker WebSocket server listens on.

            Must start with a '/', e.g. /mqtt
        """.trimIndent(),
        placeholder = "/mqtt",
        initialValue = userSettings.mqttWebSocketServerPath,
        restricted = restricted,
        validator = { it.isEmpty() || it.startsWith('/') },
        validationMessage = "Path must start with '/'",
        isMultiline = false,
        onSave = { userSettings.mqttWebSocketServerPath = it },
    )
}
