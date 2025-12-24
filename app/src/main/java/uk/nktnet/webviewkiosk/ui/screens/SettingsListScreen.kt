package com.nktnet.webview_kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingListItem
import com.nktnet.webview_kiosk.ui.components.setting.SettingsHeaderMenu

@Composable
fun SettingsListScreen(
    navController: NavController,
) {
    val settingsItems = listOf(
        Triple(
            stringResource(id = R.string.settings_web_content_title),
            stringResource(id = R.string.settings_web_content_description),
            Screen.SettingsWebContent.route
        ),
        Triple(
            stringResource(id = R.string.settings_web_browsing_title),
            stringResource(id = R.string.settings_web_browsing_description),
            Screen.SettingsWebBrowsing.route
        ),
        Triple(
            stringResource(id = R.string.settings_web_engine_title),
            stringResource(id = R.string.settings_web_engine_description),
            Screen.SettingsWebEngine.route
        ),
        Triple(
            stringResource(id = R.string.settings_web_lifecycle_title),
            stringResource(id = R.string.settings_web_lifecycle_description),
            Screen.SettingsWebLifecycle.route
        ),
        Triple(
            stringResource(id = R.string.settings_appearance_title),
            stringResource(id = R.string.settings_appearance_description),
            Screen.SettingsAppearance.route
        ),
        Triple(
            stringResource(id = R.string.settings_device_title),
            stringResource(id = R.string.settings_device_description),
            Screen.SettingsDevice.route
        ),
        Triple(
            stringResource(id = R.string.settings_js_scripts_title),
            stringResource(id = R.string.settings_js_scripts_description),
            Screen.SettingsJsScript.route
        ),
        Triple(
            "MQTT",
            "Remote management and monitoring of your kiosk device",
            Screen.SettingsMqtt.route
        ),
        Triple(
            stringResource(id = R.string.settings_about_title),
            stringResource(id = R.string.settings_about_description),
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
