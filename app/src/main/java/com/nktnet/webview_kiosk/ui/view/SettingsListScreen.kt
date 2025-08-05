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
import com.nktnet.webview_kiosk.config.Theme
import com.nktnet.webview_kiosk.ui.components.DeviceSecurityTip
import com.nktnet.webview_kiosk.ui.components.SettingsHeaderMenu

@Composable
fun SettingsListScreen(
    navController: NavController,
    themeState: MutableState<Theme>,
) {
    val settingsItems = listOf(
        Triple(
            "Appearance",
            "Theme, UI elements",
            Screen.SettingsAppearance.route
        ),
        Triple(
            "URL Control",
            "Home URL, blacklist, whitelist",
            Screen.SettingsUrlControl.route
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
