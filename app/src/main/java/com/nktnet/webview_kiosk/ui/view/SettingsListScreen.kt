package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.ui.components.DeviceSecurityTip
import com.nktnet.webview_kiosk.ui.components.import_export.SettingsHeaderMenu

@Composable
fun SettingsListScreen(navController: NavController) {
    val settingsItems = listOf(
        Triple(
            "URL Control",
            "Set allowed or blocked websites",
            Screen.SettingsUrlControl.route
        ),
        Triple(
            "Appearance",
            "Configure UI elements like theme and colors",
            Screen.SettingsAppearance.route
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 30.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        SettingsHeaderMenu()

        LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
            items(settingsItems) { (title, description, route) ->
                ListItem(
                    headlineContent = { Text(text = title) },
                    supportingContent = { Text(text = description) },
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
