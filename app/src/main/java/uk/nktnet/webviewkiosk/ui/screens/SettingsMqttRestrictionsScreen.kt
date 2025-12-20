package uk.nktnet.webviewkiosk.ui.screens

import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsMaximumPacketSizeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsReceiveMaximumSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsRequestProblemInformationSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsRequestResponseInformationSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsSendMaximumPacketSizeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsSendMaximumSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsSendTopicAliasMaximumSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsTopicAliasMaximumSetting
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(navController = navController, label = "Restrictions")
        SettingDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
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
