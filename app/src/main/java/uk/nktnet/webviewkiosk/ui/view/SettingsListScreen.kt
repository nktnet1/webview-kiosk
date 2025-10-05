package uk.nktnet.webviewkiosk.ui.view

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingsHeaderMenu

@Composable
fun SettingsListScreen(
    navController: NavController,
    themeState: MutableState<ThemeOption>,
) {
    val settingsItems = listOf(
        Triple(
            "Web Content",
            "Home URL, blacklist, whitelist, bookmark, files",
            Screen.SettingsWebContent.route
        ),
        Triple(
            "Web Browsing",
            "Refresh, navigation, history, search provider",
            Screen.SettingsWebBrowsing.route
        ),
        Triple(
            "Web Engine",
            "JavaScript, DOM storage, cookies, cache, user agent, zoom",
            Screen.SettingsWebEngine.route
        ),
        Triple(
            "Web Lifecycle",
            "Startup behaviour, auto-reset on inactivity",
            Screen.SettingsWebLifecycle.route
        ),
        Triple(
            "Appearance",
            "Theme, address bar, insets, blocked message",
            Screen.SettingsAppearance.route
        ),
        Triple(
            "Device",
            "Keep screen on, device rotation",
            Screen.SettingsDevice.route
        ),
        Triple(
            "JS Scripts",
            "Apply theme, desktop viewport, custom scripts",
            Screen.SettingsJsScript.route
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
        SettingsHeaderMenu(navController, themeState)

        SettingDivider()

        settingsItems.forEach { (title, description, route) ->
            ListItem(
                headlineContent = { Text(text = title) },
                supportingContent = {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier
                    .clickable { navController.navigate(route) }
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = MaterialTheme.shapes.medium
                    )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}
