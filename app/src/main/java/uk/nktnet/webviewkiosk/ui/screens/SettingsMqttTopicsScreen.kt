package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttControlButtons
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.SettingListItem
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttDebugLogsButton

@Composable
fun SettingsMqttTopicsScreen(navController: NavController) {
    val publishTopics = listOf(
        Triple(
            stringResource(R.string.settings_mqtt_topics_publish_event_title),
            stringResource(R.string.settings_mqtt_topics_publish_event_description),
            Screen.SettingsMqttTopicsPublishEvent.route
        ),
        Triple(
            stringResource(R.string.settings_mqtt_topics_publish_response_title),
            stringResource(R.string.settings_mqtt_topics_publish_response_description),
            Screen.SettingsMqttTopicsPublishResponse.route
        ),
    )

    val subscribeTopics = listOf(
        Triple(
            stringResource(R.string.settings_mqtt_topics_subscribe_command_title),
            stringResource(R.string.settings_mqtt_topics_subscribe_command_description),
            Screen.SettingsMqttTopicsSubscribeCommand.route
        ),
        Triple(
            stringResource(R.string.settings_mqtt_topics_subscribe_settings_title),
            stringResource(R.string.settings_mqtt_topics_subscribe_settings_description),
            Screen.SettingsMqttTopicsSubscribeSettings.route
        ),
        Triple(
            stringResource(R.string.settings_mqtt_topics_subscribe_request_title),
            stringResource(R.string.settings_mqtt_topics_subscribe_request_description),
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
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_mqtt_topics_title),
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MqttControlButtons()

            Text(
                text = stringResource(R.string.settings_mqtt_topics_publish_title),
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
                text = stringResource(R.string.settings_mqtt_topics_subscribe_title),
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
