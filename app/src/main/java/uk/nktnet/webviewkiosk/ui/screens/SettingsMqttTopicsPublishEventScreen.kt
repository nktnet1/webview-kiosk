package com.nktnet.webview_kiosk.ui.screens

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
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.event.MqttPublishEventQosSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.event.MqttPublishEventRetainSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.event.MqttPublishEventTopicSetting
import com.nktnet.webview_kiosk.ui.components.setting.permissions.MqttDebugLogsButton

@Composable
fun SettingsMqttTopicsPublishEventScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(navController = navController, label = "Event")
        SettingDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MqttControlButtons()
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            MqttPublishEventTopicSetting()
            MqttPublishEventQosSetting()
            MqttPublishEventRetainSetting()

            Spacer(modifier = Modifier.height(8.dp))
        }

        MqttDebugLogsButton(navController)
    }
}
