package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.MqttControlButtons
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.SettingListItem
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.MqttEnabledSetting
import uk.nktnet.webviewkiosk.ui.components.setting.permissions.MqttDebugLogsButton

@Composable
fun SettingsMqttScreen(navController: NavController) {
    val settingsItems = listOf(
        Triple(
            "Connection",
            "Specify how to connect to the broker server",
            Screen.SettingsMqttConnection.route
        ),
        Triple(
            "Topics",
            "Publish and subscribe topic configurations",
            Screen.SettingsMqttTopics.route
        ),
        Triple(
            "Will (LWT)",
            "Customise message for unexpected disconnect",
            Screen.SettingsMqttWill.route
        ),
        Triple(
            "Restrictions",
            "Broker and client restrictions and limitations",
            Screen.SettingsMqttRestrictions.route
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
    ) {
        SettingLabel(navController = navController, label = "MQTT")
        SettingDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MqttControlButtons()
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            MqttEnabledSetting()

            Spacer(modifier = Modifier.height(6.dp))

            settingsItems.forEach { (title, description, route) ->
                SettingListItem(title, description) { navController.navigate(route) }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        MqttDebugLogsButton(navController)

        Spacer(modifier = Modifier.height(16.dp))
    }
}
