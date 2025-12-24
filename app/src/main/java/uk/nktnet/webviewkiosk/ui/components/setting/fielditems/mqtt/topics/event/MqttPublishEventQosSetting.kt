package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.event

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
fun MqttPublishEventQosSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Publish.Event.QOS

    DropdownSettingFieldItem(
        label = stringResource(R.string.mqtt_publish_event_qos_title),
        infoText = """
            Quality of Service (QoS) ensures different message delivery guarantees
            in case of connection failures.
        """.trimIndent(),
        options = MqttQosOption.entries,
        initialValue = userSettings.mqttPublishEventQos,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttPublishEventQos = it },
        itemText = { it.getSettingLabel() },
    )
}
