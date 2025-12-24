package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.will

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
fun MqttWillTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Will.TOPIC

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_will_topic_title),
        infoText = $$"""
            The MQTT topic to publish the last will message if the client
            disconnects unexpectedly.

            All global variables are supported, e.g. you can use
            - wk/${$${MqttVariableName.USERNAME.name}}/${$${MqttVariableName.APP_INSTANCE_ID.name}}/will
        """.trimIndent(),
        placeholder = "e.g. wk/will",
        initialValue = userSettings.mqttWillTopic,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        validator = { it.isEmpty() || isValidMqttPublishTopic(it) },
        descriptionFormatter = {
            mqttVariableReplacement( it)
        },
        isMultiline = false,
        onSave = { userSettings.mqttWillTopic = it },
    )
}
