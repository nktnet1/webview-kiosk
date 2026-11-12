package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.homeassistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttHomeAssistantEnableDiscoverySetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.HomeAssistant.ENABLED

    BooleanSettingFieldItem(
        initialValue = userSettings.mqttHomeAssistantEnabled,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttHomeAssistantEnabled = it },
    )
}
