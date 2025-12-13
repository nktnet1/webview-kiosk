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
fun MqttSocketConnectTimeoutSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_socket_connect_timeout_title),
        infoText = """
            The timeout for connecting the socket to the server.

            Use 0 to disable the timeout.
        """.trimIndent(),
        placeholder = "e.g. 5",
        initialValue = userSettings.mqttSocketConnectTimeout,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.SOCKET_CONNECT_TIMEOUT),
        min = 0,
        max = 120,
        onSave = { userSettings.mqttSocketConnectTimeout = it }
    )
}
