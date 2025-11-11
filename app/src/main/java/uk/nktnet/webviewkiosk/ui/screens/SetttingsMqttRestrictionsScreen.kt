package uk.nktnet.webviewkiosk.ui.screens

import MqttRestrictionsMaximumPacketSizeSetting
import MqttRestrictionsReceiveMaximumSetting
import MqttRestrictionsRequestProblemInformationSetting
import MqttRestrictionsRequestResponseInformationSetting
import MqttRestrictionsSendMaximumPacketSizeSetting
import MqttRestrictionsSendMaximumSetting
import MqttRestrictionsSendTopicAliasMaximumSetting
import MqttRestrictionsTopicAliasMaximumSetting
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
import uk.nktnet.webviewkiosk.ui.components.setting.permissions.MqttDebugLogsButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun SettingsMqttRestrictionsScreen(navController: NavController) {
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
            SettingLabel(navController = navController, label = "MQTT Restrictions")
            SettingDivider()

            MqttControlButtons()
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            MqttRestrictionsReceiveMaximumSetting()
            MqttRestrictionsSendMaximumSetting()
            MqttRestrictionsMaximumPacketSizeSetting()
            MqttRestrictionsSendMaximumPacketSizeSetting()
            MqttRestrictionsTopicAliasMaximumSetting()
            MqttRestrictionsSendTopicAliasMaximumSetting()
            MqttRestrictionsRequestProblemInformationSetting()
            MqttRestrictionsRequestResponseInformationSetting()

            Spacer(modifier = Modifier.height(8.dp))
        }

        MqttDebugLogsButton(navController)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
