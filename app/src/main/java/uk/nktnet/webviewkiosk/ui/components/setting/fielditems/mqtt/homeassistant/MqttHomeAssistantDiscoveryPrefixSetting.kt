package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.homeassistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.HomeAssistantDiscovery
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttHomeAssistantDiscoveryPrefixSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.HomeAssistant.DISCOVERY_PREFIX

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_home_assistant_discovery_prefix_title),
        infoText =
            """
            Match Home Assistant's MQTT discovery prefix.
            Leave the default homeassistant unless Home Assistant uses a custom
            prefix.
            Reconnect MQTT after changing it.
            """
                .trimIndent(),
        placeholder = "homeassistant",
        initialValue = userSettings.mqttHomeAssistantDiscoveryPrefix,
        validator = HomeAssistantDiscovery::isValidPrefix,
        validationMessage =
            """
            Enter a non-empty MQTT prefix without wildcards.
            Remove any leading/trailing spaces or slashes.
            """
                .trimIndent(),
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        onSave = { userSettings.mqttHomeAssistantDiscoveryPrefix = it },
    )
}
