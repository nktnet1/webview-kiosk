package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttRestrictionsMaximumPacketSizeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Restrictions.MAXIMUM_PACKET_SIZE

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_restrictions_send_maximum_packet_size_title),
        infoText = """
            Maximum size in bytes of MQTT packets the client can receive.
        """.trimIndent(),
        placeholder = "e.g. 2048",
        initialValue = userSettings.mqttRestrictionsMaximumPacketSize,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = 268_435_460,
        onSave = { userSettings.mqttRestrictionsMaximumPacketSize = it }
    )
}
