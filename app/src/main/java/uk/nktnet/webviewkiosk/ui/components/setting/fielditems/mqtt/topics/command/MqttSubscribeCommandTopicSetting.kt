package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.command

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
import com.nktnet.webview_kiosk.utils.isValidMqttSubscribeTopic

@Composable
fun MqttSubscribeCommandTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_subscribe_command_topic_title),
        infoText = $$"""
            The MQTT topic name to receive commands.

            Supported variables:
            - $${MqttVariableName.APP_INSTANCE_ID.name}
            - $${MqttVariableName.USERNAME.name}

            Example:
            - wk/${$${MqttVariableName.APP_INSTANCE_ID.name}}/command
        """.trimIndent(),
        placeholder = "e.g. wk/command",
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
