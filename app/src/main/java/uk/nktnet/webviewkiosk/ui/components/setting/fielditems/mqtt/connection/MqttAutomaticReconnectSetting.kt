package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttAutomaticReconnectSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.AUTOMATIC_RECONNECT

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_automatic_reconnect_title),
        infoText = """
            When enabled, attempt to automatically reconnect to the MQTT broker
            after an unexpected disconnect.

            In ${Constants.APP_NAME}, this is implemented linearly at a fixed
            ${Constants.MQTT_AUTO_RECONNECT_INTERVAL_SECONDS}-second interval.
        """.trimIndent(),
        initialValue = userSettings.mqttAutomaticReconnect,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttAutomaticReconnect = it }
    )
}
