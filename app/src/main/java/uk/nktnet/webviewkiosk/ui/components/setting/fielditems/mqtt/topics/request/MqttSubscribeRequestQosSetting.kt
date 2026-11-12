package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.MqttQosOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun MqttSubscribeRequestQosSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Request.QOS

    DropdownSettingFieldItem(
        options = MqttQosOption.entries,
        initialValue = userSettings.mqttSubscribeRequestQos,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttSubscribeRequestQos = it },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
