package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.mqtt.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttSubscribeSettingsTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC)

    TextSettingFieldItem(
        label = "Topic",
        infoText = """
            The MQTT topic to subscribe for settings messages.
        """.trimIndent(),
        placeholder = "e.g. devices/+/settings",
        initialValue = userSettings.mqttSubscribeSettingsTopic,
        descriptionFormatter = {
            mqttVariableReplacement(systemSettings, it)
        },
        restricted = restricted,
        isMultiline = false,
        onSave = { userSettings.mqttSubscribeSettingsTopic = it }
    )
}
