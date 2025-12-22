package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.MqttVariableName
import uk.nktnet.webviewkiosk.managers.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.isValidMqttSubscribeTopic

@Composable
fun MqttSubscribeRequestTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Request.TOPIC

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_subscribe_request_topic_title),
        infoText = $$"""
            The MQTT topic name to receive requests.

            Supported variables:
            - $${MqttVariableName.APP_INSTANCE_ID.name}
            - $${MqttVariableName.USERNAME.name}

            Example:
            - wk/${$${MqttVariableName.APP_INSTANCE_ID.name}}/request
        """.trimIndent(),
        placeholder = "e.g. wk/request",
        initialValue = userSettings.mqttSubscribeRequestTopic,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        validator = { it.isEmpty() || isValidMqttSubscribeTopic(it) },
        descriptionFormatter = {
            mqttVariableReplacement(it)
        },
        isMultiline = false,
        onSave = { userSettings.mqttSubscribeRequestTopic = it }
    )
}
