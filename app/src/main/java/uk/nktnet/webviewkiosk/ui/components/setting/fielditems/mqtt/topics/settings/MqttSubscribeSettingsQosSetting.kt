package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun MqttSubscribeSettingsQosSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "QoS",
        infoText = """
            Quality of Service (QoS) ensures different message delivery guarantees
            for settings subscriptions.
        """.trimIndent(),
        options = MqttQosOption.entries,
        initialValue = userSettings.mqttSubscribeSettingsQos,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.QOS),
        onSave = { userSettings.mqttSubscribeSettingsQos = it },
        itemText = { it.label },
    )
}
