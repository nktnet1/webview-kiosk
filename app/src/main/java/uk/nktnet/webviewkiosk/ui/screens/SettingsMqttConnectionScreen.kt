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
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttAutomaticReconnectSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttCleanStartSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttClientIdSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttConnectTimeoutSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttKeepAliveSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttPasswordSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttServerHostSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttServerPortSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttWebSocketServerPathSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttUseTlsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttUsernameSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttSocketConnectTimeoutSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection.MqttUseWebSocketSetting
import uk.nktnet.webviewkiosk.ui.components.setting.permissions.MqttDebugLogsButton

@Composable
fun SettingsMqttConnectionScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
    ) {
        SettingLabel(navController = navController, label = "Connection")
        SettingDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MqttControlButtons()
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            MqttServerHostSetting()
            MqttServerPortSetting()
            MqttUseTlsSetting()
            MqttUsernameSetting()
            MqttPasswordSetting()

            MqttClientIdSetting()
            MqttCleanStartSetting()
            MqttKeepAliveSetting()
            MqttConnectTimeoutSetting()
            MqttSocketConnectTimeoutSetting()
            MqttAutomaticReconnectSetting()

            MqttUseWebSocketSetting()
            MqttWebSocketServerPathSetting()

            Spacer(modifier = Modifier.height(6.dp))
        }

        MqttDebugLogsButton(navController)

        Spacer(modifier = Modifier.height(16.dp))
    }
}
