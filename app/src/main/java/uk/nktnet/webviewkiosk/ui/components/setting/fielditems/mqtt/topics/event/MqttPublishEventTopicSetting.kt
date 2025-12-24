package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.event

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
fun MqttPublishEventTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_publish_event_topic_title),
        infoText = $$"""
            The MQTT topic to publish event messages.

            Supported variables:
            - $${MqttVariableName.EVENT_TYPE.name}
            - $${MqttVariableName.APP_INSTANCE_ID.name}
            - $${MqttVariableName.USERNAME.name}

            Example:
            - wk/event/${$${MqttVariableName.EVENT_TYPE.name}}
        """.trimIndent(),
        placeholder = $$"e.g. wk/event/${$${MqttVariableName.EVENT_TYPE.name}}",
        initialValue = userSettings.mqttPublishEventTopic,
        descriptionFormatter = {
            mqttVariableReplacement( it)
        },
        validator = { it.isEmpty() || isValidMqttPublishTopic(it) },
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        onSave = { userSettings.mqttPublishEventTopic = it }
    )
}
