package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.MqttRetainHandlingOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun MqttSubscribeCommandRetainHandlingSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING

    DropdownSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                stringResource(R.string.app_name),
            ),
        options = MqttRetainHandlingOption.entries,
        initialValue = userSettings.mqttSubscribeCommandRetainHandling,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttSubscribeCommandRetainHandling = it },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
