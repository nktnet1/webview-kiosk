package uk.nktnet.webviewkiosk.utils

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import uk.nktnet.webviewkiosk.ui.components.auth.RequireAuthWrapper

internal inline fun NavGraphBuilder.authComposable(
    route: String,
    navController: NavController,
    crossinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route) { entry ->
        NonPredictiveNavigationBackHandler(navController, entry)
        RequireAuthWrapper(navController) {
            content(entry)
        }
    }
}
