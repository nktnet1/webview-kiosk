package com.nktnet.webview_kiosk.ui.screens

import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsMaximumPacketSizeSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsReceiveMaximumSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsRequestProblemInformationSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsRequestResponseInformationSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsSendMaximumPacketSizeSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsSendMaximumSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsSendTopicAliasMaximumSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions.MqttRestrictionsTopicAliasMaximumSetting
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.ui.components.setting.MqttControlButtons
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.permissions.MqttDebugLogsButton
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
    }
}
