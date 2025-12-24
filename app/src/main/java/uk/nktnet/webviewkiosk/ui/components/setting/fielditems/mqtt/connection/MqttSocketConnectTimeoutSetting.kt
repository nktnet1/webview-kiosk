package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttSocketConnectTimeoutSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.SOCKET_CONNECT_TIMEOUT

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_socket_connect_timeout_title),
        infoText = """
            The timeout for connecting the socket to the server.

            Use 0 to disable the timeout.
        """.trimIndent(),
        placeholder = "e.g. 5",
        initialValue = userSettings.mqttSocketConnectTimeout,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = Int.MAX_VALUE,
        onSave = { userSettings.mqttSocketConnectTimeout = it }
    )
}
