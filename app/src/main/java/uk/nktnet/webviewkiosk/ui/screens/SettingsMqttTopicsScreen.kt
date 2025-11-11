package uk.nktnet.webviewkiosk.ui.screens

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
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.MqttControlButtons
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.SettingListItem
import uk.nktnet.webviewkiosk.ui.components.setting.permissions.MqttDebugLogsButton

@Composable
fun SettingsMqttTopicsScreen(navController: NavController) {
    val publishTopics = listOf(
        Triple(
            "Event",
            "Changes to device state",
            Screen.SettingsMqttTopicsPublishEvent.route
        ),
        Triple(
            "Response",
            "Fallback response channel for commands",
            Screen.SettingsMqttTopicsPublishResponse.route
        ),
    )

    val subscribeTopics = listOf(
        Triple(
            "Command",
            "React to commands from broker",
            Screen.SettingsMqttTopicsSubscribeCommand.route
        ),
        Triple(
            "Settings",
            "React to setting changes from broker",
            Screen.SettingsMqttTopicsSubscribeSettings.route
        ),
    )

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
            SettingLabel(navController = navController, label = "Topics")
            SettingDivider()

            MqttControlButtons()

            Text(
                text = "Publish",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            HorizontalDivider(
                Modifier.padding(bottom = 4.dp),
                DividerDefaults.Thickness,
                DividerDefaults.color
            )
            publishTopics.forEach { (title, description, route) ->
                SettingListItem(title, description) { navController.navigate(route) }
            }

            Text(
                text = "Subscribe",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
            )
            HorizontalDivider(
                Modifier.padding(bottom = 4.dp),
                DividerDefaults.Thickness,
                DividerDefaults.color
            )
            subscribeTopics.forEach { (title, description, route) ->
                SettingListItem(title, description) { navController.navigate(route) }
            }
        }

        MqttDebugLogsButton(navController)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
