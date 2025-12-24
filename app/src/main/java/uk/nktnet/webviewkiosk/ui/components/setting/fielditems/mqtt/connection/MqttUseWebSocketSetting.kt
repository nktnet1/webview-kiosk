package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttUseWebSocketSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.USE_WEBSOCKET

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_use_websocket_title),
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
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttUseWebSocket = it }
    )
}
