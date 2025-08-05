package com.nktnet.webview_kiosk

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun SettingsAppearanceScreen(navController: NavController) {
    Button(onClick = { navController.popBackStack() }) {
        Text("Close Appearance Settings")
    }
}
