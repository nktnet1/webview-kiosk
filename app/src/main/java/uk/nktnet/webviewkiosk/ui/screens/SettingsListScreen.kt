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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingListItem
import uk.nktnet.webviewkiosk.ui.components.setting.SettingsHeaderMenu

@Composable
fun SettingsListScreen(
    navController: NavController,
) {
    val settingsItems = listOf(
        Triple(
            stringResource(R.string.settings_web_content_title),
            stringResource(R.string.settings_web_content_description),
            Screen.SettingsWebContent.route
        ),
        Triple(
            stringResource(R.string.settings_web_browsing_title),
            stringResource(R.string.settings_web_browsing_description),
            Screen.SettingsWebBrowsing.route
        ),
        Triple(
            stringResource(R.string.settings_web_engine_title),
            stringResource(R.string.settings_web_engine_description),
            Screen.SettingsWebEngine.route
        ),
        Triple(
            stringResource(R.string.settings_web_lifecycle_title),
            stringResource(R.string.settings_web_lifecycle_description),
            Screen.SettingsWebLifecycle.route
        ),
        Triple(
            stringResource(R.string.settings_appearance_title),
            stringResource(R.string.settings_appearance_description),
            Screen.SettingsAppearance.route
        ),
        Triple(
            stringResource(R.string.settings_device_title),
            stringResource(R.string.settings_device_description),
            Screen.SettingsDevice.route
        ),
        Triple(
            stringResource(R.string.settings_js_scripts_title),
            stringResource(R.string.settings_js_scripts_description),
            Screen.SettingsJsScript.route
        ),
        Triple(
            stringResource(R.string.settings_mqtt_title),
            stringResource(R.string.settings_mqtt_description),
            Screen.SettingsMqtt.route
        ),
        Triple(
            stringResource(R.string.settings_unifiedpush_title),
            stringResource(R.string.settings_unifiedpush_description),
            Screen.SettingsUnifiedPush.route
        ),
        Triple(
            stringResource(R.string.settings_about_title),
            stringResource(R.string.settings_about_description),
            Screen.SettingsAbout.route
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        SettingsHeaderMenu(navController)
        SettingDivider()

        Column(
            modifier = Modifier
                .padding(top = 2.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            settingsItems.forEach { (title, description, route) ->
                SettingListItem(
                    title = title,
                    description = description,
                    onClick = {
                        navController.navigate(route)
                    },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
