package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.MqttCleanStartSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.MqttConnectTimeoutSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.MqttEnabledSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.MqttKeepAliveSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.MqttPasswordSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.MqttServerHostSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.MqttServerPortSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.MqttUseTlsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.MqttUsernameSetting

@Composable
fun SettingsMqttScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState())
    ) {
        SettingLabel(navController = navController, label = "MQTT")

        SettingDivider()
        MqttEnabledSetting()
        MqttServerHostSetting()
        MqttServerPortSetting()
        MqttUsernameSetting()
        MqttPasswordSetting()
        MqttUseTlsSetting()
        MqttConnectTimeoutSetting()
        MqttCleanStartSetting()
        MqttKeepAliveSetting()
    }
}
