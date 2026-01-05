package uk.nktnet.webviewkiosk.utils

import android.util.Log
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Constants
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
        Log.e(Constants.APP_SCHEME, "Failed to navigate to WebView Screen", e)
    }
}
