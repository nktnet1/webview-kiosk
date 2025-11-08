package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.mqtt.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttWillTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Will.TOPIC)

    TextSettingFieldItem(
        label = "Topic",
        infoText = "The MQTT topic to publish the last will message if the client disconnects unexpectedly.",
        placeholder = "e.g. wk/will",
        initialValue = userSettings.mqttWillTopic,
        restricted = restricted,
        descriptionFormatter = {
            mqttVariableReplacement(systemSettings, it)
        },
        isMultiline = false,
        onSave = { userSettings.mqttWillTopic = it },
    )
}
