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
fun MqttHomeAssistantEnabledSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.HomeAssistant.ENABLED

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_home_assistant_enabled_title),
        infoText =
            "Publish the kiosk as a Home Assistant MQTT device. " +
                "Includes status, all remote commands, configuration controls, " +
                "and request results. All discovered entities are enabled by default. " +
                "Enter Toast and Notification messages directly in their text controls. " +
                "Reconnect MQTT after changing this setting.\n\n" +
                "Discovery uses its own availability Last Will. Custom Will " +
                "settings apply when discovery is disabled.",
        initialValue = userSettings.mqttHomeAssistantEnabled,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttHomeAssistantEnabled = it },
    )
}
