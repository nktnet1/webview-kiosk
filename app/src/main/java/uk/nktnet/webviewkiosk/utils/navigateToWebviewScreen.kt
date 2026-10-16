package uk.nktnet.webviewkiosk.utils

import android.util.Log
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.Screen

/**
 * A saved-state signal that recreates the WebView UI without replacing its
 * NavBackStackEntry.
 */
internal const val WEBVIEW_RECREATION_REQUEST_KEY = "webviewRecreationRequest"

fun navigateToWebViewScreen(navController: NavController) {
    try {
        // The WebView is the graph's root. Removing it with an inclusive popUpTo and
        // then
        // navigating to another WebView creates a new back-stack entry while the old
        // one
        // can still be used by Navigation Compose's predictive-back transition.
        val webViewEntry =
            try {
                navController.getBackStackEntry(Screen.WebView.route)
            } catch (_: IllegalArgumentException) {
                null
            }
        if (webViewEntry == null) {
            // Nonstandard graphs (including the isolated authentication screen) may
            // start
            // without a WebView entry. Create it once rather than losing the exit
            // action.
            navController.navigate(Screen.WebView.route) { launchSingleTop = true }
            return
        }
        if (navController.currentBackStackEntry !== webViewEntry) {
            // Keep the original root (and its identity) when leaving nested settings
            // or
            // the administrator notice. Returning to an existing entry needs no
            // navigate().
            if (
                !navController.popBackStack(Screen.WebView.route, inclusive = false)
            ) {
                return
            }
        }

        // Previously this helper deliberately recreated the WebView even if it was
        // already
        // visible (for new URLs and applied settings). Retain that behaviour without
        // removing
        // the destination the back gesture is animating.
        val state = webViewEntry.savedStateHandle
        state[WEBVIEW_RECREATION_REQUEST_KEY] =
            (state.get<Int>(WEBVIEW_RECREATION_REQUEST_KEY) ?: 0) + 1
    } catch (e: Exception) {
        Log.e(Constants.APP_SCHEME, "Failed to navigate to WebView Screen", e)
    }
}
