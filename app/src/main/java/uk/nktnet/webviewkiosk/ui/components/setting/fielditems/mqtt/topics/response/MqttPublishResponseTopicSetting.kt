package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.response

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttVariableNameOption
import uk.nktnet.webviewkiosk.managers.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.isValidMqttPublishTopic

@Composable
fun MqttPublishResponseTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Publish.Response.TOPIC

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_publish_response_retain_title),
        infoText = """
            Default MQTT topic to publish reply messages to requests.

            If a responseTopic is specified in the request, either in the
            payload or in MQTT V5's metadata, the reply will be published
            to that responseTopic instead.

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
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        onSave = { userSettings.mqttPublishResponseTopic = it }
    )
}
