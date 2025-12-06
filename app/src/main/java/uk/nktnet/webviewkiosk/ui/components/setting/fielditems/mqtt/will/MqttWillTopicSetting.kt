package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttVariableNameOption
import uk.nktnet.webviewkiosk.managers.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.isValidMqttPublishTopic

@Composable
fun MqttWillTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Will.TOPIC)

    TextSettingFieldItem(
        label = "Topic",
        infoText = """
            The MQTT topic to publish the last will message if the client
            disconnects unexpectedly.

            All global variables are supported, e.g. you can use
            - wk/${'$'}{${MqttVariableNameOption.USERNAME.name}}/${'$'}{${MqttVariableNameOption.APP_INSTANCE_ID.name}}/will
        """.trimIndent(),
        placeholder = "e.g. wk/will",
        initialValue = userSettings.mqttWillTopic,
        restricted = restricted,
        validator = { it.isEmpty() || isValidMqttPublishTopic(it) },
        descriptionFormatter = {
            mqttVariableReplacement( it)
        },
        isMultiline = false,
        onSave = { userSettings.mqttWillTopic = it },
    )
}
