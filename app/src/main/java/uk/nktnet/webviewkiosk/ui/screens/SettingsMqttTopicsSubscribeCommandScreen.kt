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
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.command.MqttSubscribeCommandQosSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.command.MqttSubscribeCommandRetainHandlingSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.command.MqttSubscribeCommandRetainAsPublishedSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.command.MqttSubscribeCommandTopicSetting
import com.nktnet.webview_kiosk.ui.components.setting.permissions.MqttDebugLogsButton

@Composable
fun SettingsMqttTopicsSubscribeCommandScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
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
            MqttSubscribeCommandRetainAsPublishedSetting()

            Spacer(modifier = Modifier.height(8.dp))
        }

        MqttDebugLogsButton(navController)
    }
}
