package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun MqttSubscribeCommandQosSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = stringResource(R.string.mqtt_subscribe_command_qos_title),
        infoText = """
            Quality of Service (QoS) ensures different message delivery guarantees
            in case of connection failures.
        """.trimIndent(),
        options = MqttQosOption.entries,
        initialValue = userSettings.mqttSubscribeCommandQos,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.QOS),
        onSave = { userSettings.mqttSubscribeCommandQos = it },
        itemText = { it.getSettingLabel() },
    )
}
