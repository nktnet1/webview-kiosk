package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun MqttWillQosSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Will QoS",
        infoText = """
            Quality of Service (QoS) for the MQTT last will message.
            Determines the guarantee of message delivery in case of client disconnect.
        """.trimIndent(),
        options = MqttQosOption.entries,
        initialValue = userSettings.mqttWillQos,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Will.QOS),
        onSave = { userSettings.mqttWillQos = it },
        itemText = { it.getSettingLabel() },
    )
}
