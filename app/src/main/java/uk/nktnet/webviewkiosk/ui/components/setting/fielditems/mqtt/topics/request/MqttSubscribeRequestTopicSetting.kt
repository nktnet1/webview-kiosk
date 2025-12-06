package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.isValidMqttSubscribeTopic

@Composable
fun MqttSubscribeRequestTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Subscribe.Request.TOPIC)

    TextSettingFieldItem(
        label = "Topic",
        infoText = "The MQTT topic to subscribe for request messages.",
        placeholder = "e.g. devices/+/request",
        initialValue = userSettings.mqttSubscribeRequestTopic,
        restricted = restricted,
        validator = { it.isEmpty() || isValidMqttSubscribeTopic(it) },
        descriptionFormatter = {
            mqttVariableReplacement(it)
        },
        isMultiline = false,
        onSave = { userSettings.mqttSubscribeRequestTopic = it }
    )
}
