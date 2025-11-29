package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttVariableNameOption
import uk.nktnet.webviewkiosk.mqtt.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.isValidMqttPublishTopic

@Composable
fun MqttPublishEventTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC)

    TextSettingFieldItem(
        label = "Topic",
        infoText = """
            The MQTT topic to publish event messages.

            Supported variables:
            - ${MqttVariableNameOption.EVENT_TYPE.name}
            - ${MqttVariableNameOption.APP_INSTANCE_ID.name}
            - ${MqttVariableNameOption.USERNAME.name}

            Example:
            - wk/event/${'$'}{${MqttVariableNameOption.EVENT_TYPE.name}}
        """.trimIndent(),
        placeholder = "e.g. wk/event/${'$'}{${MqttVariableNameOption.EVENT_TYPE.name}}",
        initialValue = userSettings.mqttPublishEventTopic,
        descriptionFormatter = {
            mqttVariableReplacement( it)
        },
        validator = { it.isEmpty() || isValidMqttPublishTopic(it) },
        restricted = restricted,
        isMultiline = false,
        onSave = { userSettings.mqttPublishEventTopic = it }
    )
}
