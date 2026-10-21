package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.HomeAssistantDiscovery
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttControlButtons
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttDebugLogsButton
import uk.nktnet.webviewkiosk.utils.isValidMqttPublishTopic
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen

@Composable
fun SettingsMqttHomeAssistantScreen(navController: NavController) {
    val context = LocalContext.current
    val settings = remember { UserSettings(context) }
    val enabledKey = UserSettingsKeys.Mqtt.HomeAssistant.ENABLED
    val prefixKey = UserSettingsKeys.Mqtt.HomeAssistant.DISCOVERY_PREFIX
    val nameKey = UserSettingsKeys.Mqtt.HomeAssistant.DEVICE_NAME
    val statusKey = UserSettingsKeys.Mqtt.HomeAssistant.STATUS_TOPIC

    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 4.dp, start = 16.dp, end = 16.dp)
    ) {
        SettingLabel(
            navController,
            stringResource(R.string.settings_mqtt_home_assistant_title),
            endIcon = {
                IconButton(onClick = { navigateToWebViewScreen(navController) }) {
                    Icon(
                        painter =
                            painterResource(R.drawable.baseline_exit_to_app_24),
                        contentDescription = "Go to WebView",
                    )
                }
            },
        )
        SettingDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            MqttControlButtons()
            HorizontalDivider(Modifier.padding(top = 8.dp))
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
                initialValue = settings.mqttHomeAssistantEnabled,
                settingKey = enabledKey,
                restricted = settings.isRestricted(enabledKey),
                onSave = { settings.mqttHomeAssistantEnabled = it },
            )
            TextSettingFieldItem(
                label =
                    stringResource(R.string.mqtt_home_assistant_device_name_title),
                infoText =
                    "Optional display name, such as Kitchen kiosk. " +
                        "Leave blank to use Webview Kiosk and this app's instance ID. " +
                        "Renaming does not change the device or entity identifiers. " +
                        "Reconnect MQTT after changing it.",
                placeholder = "Kitchen kiosk",
                initialValue = settings.mqttHomeAssistantDeviceName,
                descriptionFormatter = { it.ifBlank { "Automatic" } },
                settingKey = nameKey,
                restricted = settings.isRestricted(nameKey),
                isMultiline = false,
                onSave = { settings.mqttHomeAssistantDeviceName = it },
            )
            TextSettingFieldItem(
                label =
                    stringResource(
                        R.string.mqtt_home_assistant_discovery_prefix_title
                    ),
                infoText =
                    "Match Home Assistant's MQTT discovery prefix. " +
                        "Leave the default homeassistant unless HA uses a custom " +
                        "prefix. Reconnect MQTT after changing it.",
                placeholder = "homeassistant",
                initialValue = settings.mqttHomeAssistantDiscoveryPrefix,
                validator = HomeAssistantDiscovery::isValidPrefix,
                settingKey = prefixKey,
                restricted = settings.isRestricted(prefixKey),
                isMultiline = false,
                onSave = { settings.mqttHomeAssistantDiscoveryPrefix = it },
            )
            TextSettingFieldItem(
                label =
                    stringResource(R.string.mqtt_home_assistant_status_topic_title),
                infoText =
                    "Match Home Assistant's MQTT birth/status topic. " +
                        "Leave the default homeassistant/status unless HA uses " +
                        "a custom topic. An online message triggers discovery and " +
                        "state updates, independently of the discovery prefix. " +
                        "Reconnect MQTT after changing it.",
                placeholder = "homeassistant/status",
                initialValue = settings.mqttHomeAssistantStatusTopic,
                validator = ::isValidMqttPublishTopic,
                settingKey = statusKey,
                restricted = settings.isRestricted(statusKey),
                isMultiline = false,
                onSave = { settings.mqttHomeAssistantStatusTopic = it },
            )
            Spacer(Modifier.height(8.dp))
        }
        MqttDebugLogsButton(navController)
    }
}
