package com.nktnet.webview_kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.ui.components.setting.MqttControlButtons
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.SettingListItem
import com.nktnet.webview_kiosk.ui.components.setting.permissions.MqttDebugLogsButton

@Composable
fun SettingsMqttTopicsScreen(navController: NavController) {
    val publishTopics = listOf(
        Triple(
            "Event",
            "Publish device and webview state changes",
            Screen.SettingsMqttTopicsPublishEvent.route
        ),
        Triple(
            "Response",
            "Publish replies to information requests",
            Screen.SettingsMqttTopicsPublishResponse.route
        ),
    )

    val subscribeTopics = listOf(
        Triple(
            "Command",
            "Subscribe to control commands (actions)",
            Screen.SettingsMqttTopicsSubscribeCommand.route
        ),
        Triple(
            "Settings",
            "Subscribe to setting (configuration) changes",
            Screen.SettingsMqttTopicsSubscribeSettings.route
        ),
        Triple(
            "Request",
            "Subscribe to requests for information",
            Screen.SettingsMqttTopicsSubscribeRequest.route
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(navController = navController, label = "Topics")
        SettingDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MqttControlButtons()

            Text(
                text = "Publish",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            HorizontalDivider(
                Modifier.padding(bottom = 8.dp),
                DividerDefaults.Thickness,
                DividerDefaults.color
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                publishTopics.forEach { (title, description, route) ->
                    SettingListItem(title, description) { navController.navigate(route) }
                }
            }

            Text(
                text = "Subscribe",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
            )
            HorizontalDivider(
                Modifier.padding(bottom = 8.dp),
                DividerDefaults.Thickness,
                DividerDefaults.color
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subscribeTopics.forEach { (title, description, route) ->
                    SettingListItem(title, description) { navController.navigate(route) }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        MqttDebugLogsButton(navController)
    }
}
