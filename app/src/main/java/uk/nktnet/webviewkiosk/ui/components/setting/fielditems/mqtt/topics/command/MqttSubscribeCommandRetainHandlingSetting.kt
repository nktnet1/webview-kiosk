package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttRetainHandlingOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun MqttSubscribeCommandRetainHandlingSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Retain Handling",
        infoText = "Control how retained messages are delivered for command subscriptions.",
        options = MqttRetainHandlingOption.entries,
        initialValue = userSettings.mqttSubscribeCommandRetainHandling,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING),
        onSave = { userSettings.mqttSubscribeCommandRetainHandling = it },
        itemText = { it.getSettingLabel() },
    )
}
