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
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command.MqttSubscribeCommandQosSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command.MqttSubscribeCommandRetainHandlingSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command.MqttSubscribeCommandRetainedAsPublishedSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command.MqttSubscribeCommandTopicSetting
import uk.nktnet.webviewkiosk.ui.components.setting.permissions.MqttDebugLogsButton

@Composable
fun SettingsMqttTopicsSubscribeCommandScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
    ) {
        SettingLabel(navController = navController, label = "Command")
        SettingDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MqttControlButtons()
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            MqttSubscribeCommandTopicSetting()
            MqttSubscribeCommandQosSetting()
            MqttSubscribeCommandRetainHandlingSetting()
            MqttSubscribeCommandRetainedAsPublishedSetting()

            Spacer(modifier = Modifier.height(8.dp))
        }

        MqttDebugLogsButton(navController)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
