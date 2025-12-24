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
        min = 0,
        placeholder = "e.g. 1883",
        onSave = { userSettings.mqttServerPort = it }
    )
}
