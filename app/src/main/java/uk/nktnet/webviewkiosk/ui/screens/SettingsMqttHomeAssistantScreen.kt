package uk.nktnet.webviewkiosk.ui.screens

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.HomeAssistantDiscovery
import uk.nktnet.webviewkiosk.config.mqtt.HomeAssistantScripts
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeAlertDialog as AlertDialog
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttControlButtons
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttDebugLogsButton
import uk.nktnet.webviewkiosk.utils.isValidMqttPublishTopic
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen
import uk.nktnet.webviewkiosk.utils.normaliseInfoText

@Composable
fun SettingsMqttHomeAssistantScreen(navController: NavController) {
    val context = LocalContext.current
    val settings = remember { UserSettings(context) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
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
                Row {
                    HomeAssistantInfoButton(
                        title = "Home Assistant setup",
                        infoText =
                            "Use the same MQTT broker as Home Assistant, enable " +
                                "discovery, then connect or restart MQTT. Find the " +
                                "kiosk under Home Assistant's Settings → Devices & " +
                                "services → MQTT.\n\n" +
                                "Controls, settings, and request results are discovered " +
                                "automatically. No scripts, YAML edits, or additional " +
                                "HA integration are needed. Leave the device name blank " +
                                "for an automatic name.\n\n" +
                                "Only change the discovery prefix or status topic if " +
                                "Home Assistant uses custom values. These settings and " +
                                "optional script export are under Advanced settings.",
                    )
                    IconButton(
                        onClick = { navigateToWebViewScreen(navController) }
                    ) {
                        Icon(
                            painter =
                                painterResource(R.drawable.baseline_exit_to_app_24),
                            contentDescription = "Go to WebView",
                        )
                    }
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
                        "No scripts or YAML edits are required in Home Assistant. " +
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
            HomeAssistantInfoRow(
                title = "Actions",
                infoText =
                    "In Home Assistant's Tools → Actions (or Developer tools → " +
                        "Actions), choose notify.send_message and select one " +
                        "specific kiosk entity.\n\n" +
                        "Toast shows a brief Android popup. Notification creates " +
                        "an Android notification. Enter the text as Message.\n\n" +
                        "For optional command fields, long settings values, or " +
                        "filtered requests, select Command JSON, Settings JSON, or " +
                        "Request JSON and enter the complete JSON envelope as " +
                        "Message. The kiosk is targeted automatically; no scripts " +
                        "are required.\n\n" +
                        "Opening a notifier's details shows history rather than " +
                        "a message editor. Unknown means no last-send time is " +
                        "recorded; it is not the connection status. Request results " +
                        "appear in the corresponding result sensors.",
            )
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(
                    if (showAdvanced) {
                        "Hide advanced settings"
                    } else {
                        "Show advanced settings"
                    }
                )
            }
            if (showAdvanced) {
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
                        stringResource(
                            R.string.mqtt_home_assistant_status_topic_title
                        ),
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val scripts =
                                HomeAssistantScripts.create(
                                    HomeAssistantDiscovery(
                                        SystemSettings(context).appInstanceId,
                                        "homeassistant",
                                    ),
                                    settings.mqttHomeAssistantDeviceName,
                                )
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipData.newPlainText(
                                            "Home Assistant scripts",
                                            scripts,
                                        )
                                        .toClipEntry()
                                )
                                ToastManager.show(
                                    context,
                                    "Home Assistant scripts copied.",
                                )
                            }
                        },
                    ) {
                        Text("Copy Home Assistant scripts")
                    }
                    HomeAssistantInfoButton(
                        title = "Optional scripts",
                        infoText =
                            "The discovered entities work without scripts. Export is " +
                                "optional if you prefer three named actions with a " +
                                "Payload editor instead of the JSON notifiers.\n\n" +
                                "Merge the copied entries into Home Assistant's " +
                                "scripts.yaml without replacing existing scripts, then " +
                                "run script.reload. Send command, Update settings, and " +
                                "Request data appear in Tools → Actions. The scripts " +
                                "already contain this kiosk's target and MQTT topics. " +
                                "No broker credentials are included.\n\n" +
                                "MQTT discovery cannot install scripts automatically. " +
                                "All existing JSON notifiers and automations keep working.",
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        MqttDebugLogsButton(navController)
    }
}

@Composable
private fun HomeAssistantInfoRow(title: String, infoText: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        HomeAssistantInfoButton(title, infoText)
    }
}

@Composable
private fun HomeAssistantInfoButton(title: String, infoText: String) {
    var showInfo by rememberSaveable { mutableStateOf(false) }

    IconButton(onClick = { showInfo = true }) {
        Icon(
            painter = painterResource(R.drawable.baseline_info_24),
            contentDescription = "$title information",
        )
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(normaliseInfoText(infoText)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Close") }
            },
        )
    }
}
