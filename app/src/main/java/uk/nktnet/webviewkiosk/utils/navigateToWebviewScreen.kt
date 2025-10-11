package com.nktnet.webview_kiosk.utils

import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.Screen

fun navigateToWebViewScreen(navController: NavController) {
    navController.navigate(Screen.WebView.route) {
        popUpTo(Screen.Settings.route) { inclusive = true }
    }
}
