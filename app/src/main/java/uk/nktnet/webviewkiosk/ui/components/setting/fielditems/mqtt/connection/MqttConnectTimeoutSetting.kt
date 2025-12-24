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
fun MqttConnectTimeoutSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.CONNECT_TIMEOUT

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_connect_timeout_title),
        infoText = """
            The timeout between sending the Connect and receiving the ConnAck message.

            Use 0 to disable the timeout.
        """.trimIndent(),
        placeholder = "e.g. 30",
        initialValue = userSettings.mqttConnectTimeout,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = Int.MAX_VALUE,
        onSave = { userSettings.mqttConnectTimeout = it }
    )
}
