package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttServerHostSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.SERVER_HOST)

    TextSettingFieldItem(
        label = "Server Host",
        infoText = """
            The hostname or IP address of the MQTT broker the app should connect to.

            For example,
            - 192.168.1.190
            - broker.hivemq.com
            - broker.emqx.io
        """.trimIndent(),
        placeholder = "e.g. broker.example.com",
        initialValue = userSettings.mqttServerHost,
        restricted = restricted,
        isMultiline = false,
        onSave = { userSettings.mqttServerHost = it }
    )
}
