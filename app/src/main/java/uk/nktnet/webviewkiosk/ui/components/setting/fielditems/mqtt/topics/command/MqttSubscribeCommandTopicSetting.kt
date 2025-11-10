package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.mqtt.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.isValidMqttSubscribeTopic

@Composable
fun MqttSubscribeCommandTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC)

    TextSettingFieldItem(
        label = "Topic",
        infoText = "The MQTT topic to subscribe for command messages.",
        placeholder = "e.g. devices/+/command",
        initialValue = userSettings.mqttSubscribeCommandTopic,
        restricted = restricted,
        validator = { isValidMqttSubscribeTopic(it) },
        descriptionFormatter = {
            mqttVariableReplacement(systemSettings, it)
        },
        isMultiline = false,
        onSave = { userSettings.mqttSubscribeCommandTopic = it }
    )
}
