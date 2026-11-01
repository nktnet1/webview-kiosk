package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.settings

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

@Composable
fun MqttSubscribeSettingsRetainHandlingSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_HANDLING

    DropdownSettingFieldItem(
        label =
            stringResource(R.string.mqtt_subscribe_settings_retain_handling_title),
        infoText =
            stringResource(
                R.string.setting_info_mqtt_subscribe_settings_retain_handling,
                stringResource(R.string.app_name),
            ),
        options = MqttRetainHandlingOption.entries,
        initialValue = userSettings.mqttSubscribeSettingsRetainHandling,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttSubscribeSettingsRetainHandling = it },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
