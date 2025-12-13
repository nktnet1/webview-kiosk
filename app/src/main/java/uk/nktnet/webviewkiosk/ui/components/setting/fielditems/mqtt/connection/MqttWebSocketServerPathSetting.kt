package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttWebSocketServerPathSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.WEBSOCKET_SERVER_PATH

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_websocket_server_path_title),
        infoText = """
            The path the MQTT broker WebSocket server listens on.

            Must start with a '/', e.g. /mqtt
        """.trimIndent(),
        placeholder = "/mqtt",
        initialValue = userSettings.mqttWebSocketServerPath,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        validator = { it.isEmpty() || it.startsWith('/') },
        validationMessage = "Path must start with '/'",
        isMultiline = false,
        onSave = { userSettings.mqttWebSocketServerPath = it },
    )
}
