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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.homeassistant.MqttHomeAssistantDeviceNameSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.homeassistant.MqttHomeAssistantDiscoveryPrefixSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.homeassistant.MqttHomeAssistantEnableDiscoverySetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.homeassistant.MqttHomeAssistantStatusTopicSetting
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttControlButtons
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttDebugLogsButton
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen

@Composable
fun SettingsMqttHomeAssistantScreen(navController: NavController) {
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
            MqttHomeAssistantEnableDiscoverySetting()
            MqttHomeAssistantDeviceNameSetting()
            MqttHomeAssistantDiscoveryPrefixSetting()
            MqttHomeAssistantStatusTopicSetting()
            Spacer(Modifier.height(8.dp))
        }
        MqttDebugLogsButton(navController)
    }
}
