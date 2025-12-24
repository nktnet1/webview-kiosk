package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.mqtt.MqttQosOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun MqttWillQosSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Will.QOS

    DropdownSettingFieldItem(
        label = stringResource(R.string.mqtt_will_qos_title),
        infoText = """
            Quality of Service (QoS) for the MQTT last will message.
            Determines the guarantee of message delivery in case of client disconnect.
        """.trimIndent(),
        options = MqttQosOption.entries,
        initialValue = userSettings.mqttWillQos,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttWillQos = it },
        itemText = { it.getSettingLabel() },
    )
}
