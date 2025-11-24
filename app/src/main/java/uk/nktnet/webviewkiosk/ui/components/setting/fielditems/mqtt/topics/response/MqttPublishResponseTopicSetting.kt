package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.response

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
fun MqttPublishResponseTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Publish.Response.TOPIC)

    TextSettingFieldItem(
        label = "Topic",
        infoText = """
            The MQTT topic to publish response messages.

            Supported variables:
            - ${MqttVariableNameOption.RESPONSE_TYPE.name}
            - ${MqttVariableNameOption.APP_INSTANCE_ID.name}
            - ${MqttVariableNameOption.USERNAME.name}

            Example:
            - wk/response/${'$'}{${MqttVariableNameOption.RESPONSE_TYPE.name}}
        """.trimIndent(),
        placeholder = "e.g. wk/response/${'$'}{${MqttVariableNameOption.RESPONSE_TYPE.name}}",
        initialValue = userSettings.mqttPublishResponseTopic,
        descriptionFormatter = { mqttVariableReplacement(it) },
        validator = { it.isEmpty() || isValidMqttPublishTopic(it) },
        restricted = restricted,
        isMultiline = false,
        onSave = { userSettings.mqttPublishResponseTopic = it }
    )
}
