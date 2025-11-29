package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttConnectTimeoutSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Connect Timeout (seconds)",
        infoText = """
            The timeout between sending the Connect and receiving the ConnAck message.

            Use 0 to disable the timeout.
        """.trimIndent(),
        placeholder = "e.g. 30",
        initialValue = userSettings.mqttConnectTimeout,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.CONNECT_TIMEOUT),
        min = 0,
        max = 300,
        onSave = { userSettings.mqttConnectTimeout = it }
    )
}
