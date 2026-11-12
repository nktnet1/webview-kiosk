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
        placeholder = stringResource(R.string.ui_homeassistant),
        initialValue = userSettings.mqttHomeAssistantDiscoveryPrefix,
        validator = HomeAssistantDiscovery::isValidPrefix,
        validationMessage =
            stringResource(R.string.ui_mqtt_homeassistant_prefix_validation),
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        onSave = { userSettings.mqttHomeAssistantDiscoveryPrefix = it },
    )
}
