@file:Suppress("UnusedVariable")

package uk.nktnet.webviewkiosk.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.createGraph
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.LocalSettingsSearchTarget
import uk.nktnet.webviewkiosk.ui.components.setting.SettingsSearchTarget
import uk.nktnet.webviewkiosk.ui.components.webview.ContentBlockingGate
import uk.nktnet.webviewkiosk.utils.NonPredictiveNavigationBackHandler
import uk.nktnet.webviewkiosk.utils.WEBVIEW_RECREATION_REQUEST_KEY
import uk.nktnet.webviewkiosk.utils.authComposable

@Composable
fun SetupNavHost(navController: NavHostController) {
    val context = LocalContext.current
    val blocker = remember(context) { ContentBlockingService.get(context) }
    SetupNavHost(navController) {
        ContentBlockingGate(blocker) { WebviewScreen(navController) }
    }
}

/**
 * Internal content seam permits deterministic Compose regression tests without a
 * real WebView.
 */
@Composable
internal fun SetupNavHost(
    navController: NavHostController,
    webViewContent: @Composable () -> Unit,
) {
    KeepLegacyInsetsListenerAttached()

    // Preserve the graph across recomposition and when the NavHost temporarily
    // leaves
    // composition (for example while a shared file is being imported). Replacing the
    // graph can invalidate entries still referenced by an in-flight predictive back.
    val graph =
        remember(navController) {
            runCatching { navController.graph }.getOrNull()
                ?: navController.createGraph(
                    startDestination = Screen.WebView.route
                ) {
                    composable(Screen.WebView.route) { entry ->
                        WebViewRootDestination(entry, webViewContent)
                    }
                    composable(Screen.AdminRestrictionsChanged.route) { entry ->
                        NonPredictiveNavigationBackHandler(navController, entry)
                        AdminRestrictionsChangedScreen(navController)
                    }
                    navigation(
                        startDestination = Screen.Settings.route,
                        route = "settings_list",
                    ) {
                        for (page in settingsPages) {
                            authComposable(page.route, navController) { entry ->
                                val targetId by
                                    entry.savedStateHandle
                                        .getStateFlow<String?>(
                                            SETTINGS_SEARCH_TARGET_KEY,
                                            null,
                                        )
                                        .collectAsState()
                                CompositionLocalProvider(
                                    LocalSettingsSearchTarget provides
                                        targetId?.let { id ->
                                            SettingsSearchTarget(
                                                id = id,
                                                onConsumed = {
                                                    entry.savedStateHandle.set<
                                                        String?
                                                    >(
                                                        SETTINGS_SEARCH_TARGET_KEY,
                                                        null,
                                                    )
                                                },
                                            )
                                        }
                                ) {
                                    page.content(navController)
                                }
                            }
                        }
                    }
                }
        }

    NavHost(navController = navController, graph = graph)
}

/** Retain the NavBackStackEntry while replacing only the WebView's composition. */
@Composable
internal fun WebViewRootDestination(
    entry: NavBackStackEntry,
    content: @Composable () -> Unit,
) {
    val recreationRequest by
        entry.savedStateHandle
            .getStateFlow(WEBVIEW_RECREATION_REQUEST_KEY, 0)
            .collectAsState()
    key(recreationRequest) { content() }
}

@Composable
private fun KeepLegacyInsetsListenerAttached() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        // Authentication placeholders have no inset consumers. Keep the activity
        // listener
        // attached across navigation so legacy inset animations finish and clear
        // their
        // cached animation state.
        // The transition hides this issue, but can be reproduced by adding the
        // following
        // properties to NavHost:
        //     enterTransition = { EnterTransition.None },
        //     exitTransition = { ExitTransition.None },
        //     popEnterTransition = { EnterTransition.None },
        //     popExitTransition = { ExitTransition.None },
        @Suppress("Unused") val activityImeInsets = WindowInsets.ime
    }
}
