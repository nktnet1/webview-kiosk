package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.isValidMqttSubscribeTopic

@Composable
fun MqttSubscribeCommandTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_subscribe_command_topic_title),
        infoText = "The MQTT topic to subscribe for command messages.",
        placeholder = "e.g. devices/+/command",
        initialValue = userSettings.mqttSubscribeCommandTopic,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        validator = { it.isEmpty() || isValidMqttSubscribeTopic(it) },
        descriptionFormatter = {
            mqttVariableReplacement( it)
        },
        isMultiline = false,
        onSave = { userSettings.mqttSubscribeCommandTopic = it }
    )
}
