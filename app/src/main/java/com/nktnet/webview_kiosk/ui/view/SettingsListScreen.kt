package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.ui.components.setting.DeviceSecurityTip
import com.nktnet.webview_kiosk.ui.components.setting.SettingsHeaderMenu

@Composable
fun SettingsListScreen(
    navController: NavController,
    themeState: MutableState<ThemeOption>,
) {
    val settingsItems = listOf(
        Triple(
            "Web Content",
            "Home URL, blacklist, whitelist",
            Screen.SettingsWebContent.route
        ),

        Triple(
            "Web Browsing",
            "Refresh, navigation, search provider",
            Screen.SettingsWebBrowsing.route
        ),
        Triple(
            "Appearance",
            "Theme, address bar, custom blocked message",
            Screen.SettingsAppearance.route
        ),

        Triple(
            "Device",
            "Keep screen on",
            Screen.SettingsDevice.route
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SettingsHeaderMenu(navController, themeState)

        LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
            items(settingsItems) { (title, description, route) ->
                ListItem(
                    headlineContent = { Text(text = title) },
                    supportingContent = {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic)
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
        }
        DeviceSecurityTip()
    }
}
