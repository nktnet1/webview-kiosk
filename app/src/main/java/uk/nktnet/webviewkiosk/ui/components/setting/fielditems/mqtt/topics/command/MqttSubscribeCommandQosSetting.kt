package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun MqttSubscribeCommandQosSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "QoS",
        infoText = "Quality of Service for MQTT command topic messages.",
        options = MqttQosOption.entries,
        initialValue = userSettings.mqttSubscribeCommandQos,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.QOS),
        onSave = { userSettings.mqttSubscribeCommandQos = it },
        itemText = {
            when (it) {
                MqttQosOption.AT_MOST_ONCE -> "At most once"
                MqttQosOption.AT_LEAST_ONCE -> "At least once"
                MqttQosOption.EXACTLY_ONCE -> "Exactly once"
            }
        }
    )
}
