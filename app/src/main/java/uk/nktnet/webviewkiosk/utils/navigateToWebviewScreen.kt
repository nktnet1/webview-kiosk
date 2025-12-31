package uk.nktnet.webviewkiosk.utils

import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Screen

fun navigateToWebViewScreen(navController: NavController) {
    try {
        navController.navigate(Screen.WebView.route) {
            launchSingleTop = true
            popUpTo(Screen.WebView.route) {
                inclusive = true
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
