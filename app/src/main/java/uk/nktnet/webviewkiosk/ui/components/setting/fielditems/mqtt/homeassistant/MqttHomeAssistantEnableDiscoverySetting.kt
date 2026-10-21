package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.homeassistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttHomeAssistantEnableDiscoverySetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.HomeAssistant.ENABLED

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_home_assistant_enabled_title),
        infoText =
            """
            Publish the kiosk's status, controls, and settings in Home Assistant.
            Reconnect MQTT after changing this setting.

            Online/offline status is managed automatically while discovery is
            enabled.
            Custom MQTT Will settings apply when discovery is disabled.
            """
                .trimIndent(),
        initialValue = userSettings.mqttHomeAssistantEnabled,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttHomeAssistantEnabled = it },
    )
}
