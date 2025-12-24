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
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.settings.MqttSubscribeSettingsQosSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.settings.MqttSubscribeSettingsRetainHandlingSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.settings.MqttSubscribeSettingsRetainAsPublishedSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.settings.MqttSubscribeSettingsTopicSetting
import com.nktnet.webview_kiosk.ui.components.setting.permissions.MqttDebugLogsButton

@Composable
fun SettingsMqttTopicsSubscribeSettingsScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(navController = navController, label = "Settings")
        SettingDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MqttControlButtons()
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            MqttSubscribeSettingsTopicSetting()
            MqttSubscribeSettingsQosSetting()
            MqttSubscribeSettingsRetainHandlingSetting()
            MqttSubscribeSettingsRetainAsPublishedSetting()

            Spacer(modifier = Modifier.height(8.dp))
        }

        MqttDebugLogsButton(navController)
    }
}
