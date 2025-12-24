package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.response

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
fun MqttPublishResponseQosSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Publish.Response.QOS

    DropdownSettingFieldItem(
        label = stringResource(R.string.mqtt_publish_response_qos_title),
        infoText = """
            Quality of Service (QoS) ensures different message delivery guarantees
            for response messages in case of connection failures.
        """.trimIndent(),
        options = MqttQosOption.entries,
        initialValue = userSettings.mqttPublishResponseQos,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttPublishResponseQos = it },
        itemText = { it.getSettingLabel() },
    )
}
