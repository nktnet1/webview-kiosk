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

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_home_assistant_device_name_title),
        infoText =
            """
            Optional display name, such as Home kiosk.
            Leave blank to use Webview Kiosk and this app's instance ID.
            Renaming does not change the device or entity identifiers.
            Reconnect MQTT after changing it.
            """
                .trimIndent(),
        placeholder = "Home kiosk",
        initialValue = userSettings.mqttHomeAssistantDeviceName,
        descriptionFormatter = { it.ifBlank { "Automatic" } },
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        onSave = { userSettings.mqttHomeAssistantDeviceName = it },
    )
}
