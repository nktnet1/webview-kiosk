package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.response

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.mqtt.MqttVariableName
import com.nktnet.webview_kiosk.managers.MqttManager.mqttVariableReplacement
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem
import com.nktnet.webview_kiosk.utils.isValidMqttPublishTopic

@Composable
fun MqttPublishResponseTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Publish.Response.TOPIC

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_publish_response_topic_title),
        infoText = $$"""
            Default MQTT topic to publish reply messages to requests.

            If a responseTopic is specified in the request, either in the
            payload or in MQTT V5's metadata, the reply will be published
            to that responseTopic instead.

            Supported variables:
            - $${MqttVariableName.RESPONSE_TYPE.name}
            - $${MqttVariableName.APP_INSTANCE_ID.name}
            - $${MqttVariableName.USERNAME.name}

            Example:
            - wk/response/${$${MqttVariableName.RESPONSE_TYPE.name}}
        """.trimIndent(),
        placeholder = $$"e.g. wk/response/${$${MqttVariableName.RESPONSE_TYPE.name}}",
        initialValue = userSettings.mqttPublishResponseTopic,
        descriptionFormatter = { mqttVariableReplacement(it) },
        validator = { it.isEmpty() || isValidMqttPublishTopic(it) },
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        onSave = { userSettings.mqttPublishResponseTopic = it }
    )
}
