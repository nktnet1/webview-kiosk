package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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
            "Web Content",
            "Home URL, blacklist, whitelist, bookmark, files",
            Screen.SettingsWebContent.route
        ),
        Triple(
            "Web Browsing",
            "Refresh, navigation, history, bookmark, search provider",
            Screen.SettingsWebBrowsing.route
        ),
        Triple(
            "Web Engine",
            "JavaScript, DOM storage, cookies, cache, user agent, zoom",
            Screen.SettingsWebEngine.route
        ),
        Triple(
            "Web Lifecycle",
            "Lock on launch, reset on launch, reset on inactivity",
            Screen.SettingsWebLifecycle.route
        ),
        Triple(
            "Appearance",
            "Theme, address bar, insets, immersive, blocked message",
            Screen.SettingsAppearance.route
        ),
        Triple(
            "Device",
            "Timeout, rotation, camera, microphone, location, unlock shortcut",
            Screen.SettingsDevice.route
        ),
        Triple(
            "JS Scripts",
            "Apply theme, desktop viewport, custom scripts",
            Screen.SettingsJsScript.route
        ),
        Triple(
            "MQTT",
            "Automation, change settings remotely",
            Screen.SettingsMqtt.route
        ),
        Triple(
            "About",
            "Package name, app version, debug build, installer",
            Screen.SettingsAbout.route
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        SettingsHeaderMenu(navController)

        SettingDivider()

        settingsItems.forEach { (title, description, route) ->
            SettingListItem(title, description) { navController.navigate(route) }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
