package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.response

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun MqttPublishResponseQosSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "QoS",
        infoText = """
            Quality of Service (QoS) ensures different message delivery guarantees
            for response messages in case of connection failures.
        """.trimIndent(),
        options = MqttQosOption.entries,
        initialValue = userSettings.mqttPublishResponseQos,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Publish.Response.QOS),
        onSave = { userSettings.mqttPublishResponseQos = it },
        itemText = { it.getSettingLabel() },
    )
}
