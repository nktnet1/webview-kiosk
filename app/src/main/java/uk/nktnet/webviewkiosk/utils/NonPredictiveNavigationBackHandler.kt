package uk.nktnet.webviewkiosk.utils

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Settings and the administrator notice can be popped programmatically (including
 * from external intents and timed callbacks) while a system back gesture is
 * starting.
 *
 * Navigation Compose's predictive-back handler can then try to transition an entry
 * that has already left the back stack. Intercept back at these destinations and
 * perform a regular pop instead. The WebView root deliberately keeps its existing
 * activity-level back/long-press handling.
 */
@Composable
internal fun NonPredictiveNavigationBackHandler(
    navController: NavController,
    entry: NavBackStackEntry,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    BackHandler(enabled = currentEntry === entry) {
        // The handler may remain enabled for one frame after an external pop.
        if (navController.currentBackStackEntry === entry) {
            navController.popBackStack()
        }
    }
}
