package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.homeassistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttHomeAssistantDeviceNameSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.HomeAssistant.DEVICE_NAME

    val automaticLabel = stringResource(R.string.ui_automatic)

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_home_assistant_device_name_title),
        infoText =
            stringResource(
                R.string.setting_info_mqtt_home_assistant_device_name,
                stringResource(R.string.app_name),
            ),
        placeholder = stringResource(R.string.ui_home_kiosk),
        initialValue = userSettings.mqttHomeAssistantDeviceName,
        descriptionFormatter = { it.ifBlank { automaticLabel } },
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        onSave = { userSettings.mqttHomeAssistantDeviceName = it },
    )
}
