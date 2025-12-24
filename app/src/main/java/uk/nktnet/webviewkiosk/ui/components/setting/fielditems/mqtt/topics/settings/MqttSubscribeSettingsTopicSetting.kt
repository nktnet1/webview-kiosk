package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.settings

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
fun MqttSubscribeSettingsTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_subscribe_settings_topic_title),
        infoText = $$"""
            The MQTT topic name to receive commands.

            Supported variables:
            - $${MqttVariableName.APP_INSTANCE_ID.name}
            - $${MqttVariableName.USERNAME.name}

            Example:
            - wk/${$${MqttVariableName.APP_INSTANCE_ID.name}}/settings
        """.trimIndent(),
        placeholder = "e.g. wk/settings",
        initialValue = userSettings.mqttSubscribeSettingsTopic,
        validator = { it.isEmpty() || isValidMqttSubscribeTopic(it) },
        descriptionFormatter = {
            mqttVariableReplacement( it)
        },
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        onSave = { userSettings.mqttSubscribeSettingsTopic = it }
    )
}
