package uk.nktnet.webviewkiosk.utils

import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Screen

fun navigateToWebViewScreen(navController: NavController) {
    navController.navigate(Screen.WebView.route) {
        popUpTo(Screen.Settings.route) { inclusive = true }
    }
}
