package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttKeepAliveSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_keep_alive_title),
        infoText = """
            The time interval (in seconds) in which the client sends a ping to the broker
            if no other MQTT packets are sent during this period of time.

            It is used to determine if the connection is still up.
        """.trimIndent(),
        placeholder = "e.g. 60",
        initialValue = userSettings.mqttKeepAlive,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = 65535,
        onSave = { userSettings.mqttKeepAlive = it }
    )
}
