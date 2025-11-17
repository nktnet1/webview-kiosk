package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.ui.components.setting.MqttControlButtons
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.request.MqttSubscribeRequestQosSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.request.MqttSubscribeRequestRetainHandlingSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.request.MqttSubscribeRequestRetainedAsPublishedSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.request.MqttSubscribeRequestTopicSetting
import uk.nktnet.webviewkiosk.ui.components.setting.permissions.MqttDebugLogsButton

@Composable
fun SettingsMqttTopicsSubscribeRequestScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            SettingLabel(navController = navController, label = "Request")
            SettingDivider()

            MqttControlButtons()
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            MqttSubscribeRequestTopicSetting()
            MqttSubscribeRequestQosSetting()
            MqttSubscribeRequestRetainHandlingSetting()
            MqttSubscribeRequestRetainedAsPublishedSetting()

            Spacer(modifier = Modifier.height(8.dp))
        }

        MqttDebugLogsButton(navController)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
