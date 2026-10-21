package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.homeassistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.isValidMqttPublishTopic

@Composable
fun MqttHomeAssistantStatusTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.HomeAssistant.STATUS_TOPIC

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_home_assistant_status_topic_title),
        infoText =
            """
            Match Home Assistant's MQTT birth/status topic.
            Leave homeassistant/status unless Home Assistant uses a custom topic.
            Reconnect MQTT after changing it.
            """
                .trimIndent(),
        placeholder = "homeassistant/status",
        initialValue = userSettings.mqttHomeAssistantStatusTopic,
        validator = ::isValidMqttPublishTopic,
        validationMessage = "Enter a non-empty MQTT topic without + or #.",
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        onSave = { userSettings.mqttHomeAssistantStatusTopic = it },
    )
}
