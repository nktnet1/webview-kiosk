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
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillDelayIntervalSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillMessageExpiryIntervalSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillPayloadSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillQosSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillRetainSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillTopicSetting
import uk.nktnet.webviewkiosk.ui.components.setting.permissions.MqttDebugLogsButton

@Composable
fun SettingsMqttWillScreen(navController: NavController) {
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
            SettingLabel(navController = navController, label = "Will (LWT)")
            SettingDivider()

            MqttControlButtons()
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            MqttWillTopicSetting()
            MqttWillQosSetting()
            MqttWillPayloadSetting()
            MqttWillRetainSetting()
            MqttWillMessageExpiryIntervalSetting()
            MqttWillDelayIntervalSetting()

            Spacer(modifier = Modifier.height(8.dp))
        }

        MqttDebugLogsButton(navController)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
