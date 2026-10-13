@file:Suppress("UnusedVariable")

package uk.nktnet.webviewkiosk.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.createGraph
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.utils.WEBVIEW_RECREATION_REQUEST_KEY
import uk.nktnet.webviewkiosk.utils.authComposable

@Composable
fun SetupNavHost(
    navController: NavHostController,
) {
    KeepLegacyInsetsListenerAttached()

    val settingsScreens: List<Pair<String, @Composable () -> Unit>> = remember(navController) {
        listOf(
            Screen.Settings.route to { SettingsListScreen(navController) },
            Screen.SettingsMoreActions.route to { SettingsMoreActionsScreen(navController) },
            Screen.SettingsWebContent.route to { SettingsWebContentScreen(navController) },
            Screen.SettingsWebContentFiles.route to { SettingsWebContentFilesScreen(navController) },
            Screen.SettingsWebBrowsing.route to { SettingsWebBrowsingScreen(navController) },
            Screen.SettingsWebBrowsingSitePermissions.route to {
                SettingsWebBrowsingSitePermissionsScreen(navController)
            },
            Screen.SettingsWebEngine.route to { SettingsWebEngineScreen(navController) },
            Screen.SettingsWebLifecycle.route to { SettingsWebLifecycleScreen(navController) },
            Screen.SettingsAppearance.route to { SettingsAppearanceScreen(navController) },
            Screen.SettingsDevice.route to { SettingsDeviceScreen(navController) },
            Screen.SettingsDeviceOwner.route to { SettingsDeviceOwnerScreen(navController) },
            Screen.SettingsJsScript.route to { SettingsJsScriptsScreen(navController) },
            Screen.SettingsMqtt.route to { SettingsMqttScreen(navController) },
            Screen.SettingsMqttConnection.route to { SettingsMqttConnectionScreen(navController) },
            Screen.SettingsMqttWill.route to { SettingsMqttWillScreen(navController) },
            Screen.SettingsMqttRestrictions.route to { SettingsMqttRestrictionsScreen(navController) },
            Screen.SettingsMqttDebug.route to { SettingsMqttDebugScreen(navController) },
            Screen.SettingsMqttTopics.route to { SettingsMqttTopicsScreen(navController) },
            Screen.SettingsMqttTopicsPublishEvent.route to {
                SettingsMqttTopicsPublishEventScreen(navController)
            },
            Screen.SettingsMqttTopicsPublishResponse.route to {
                SettingsMqttTopicsPublishResponseScreen(navController)
            },
            Screen.SettingsMqttTopicsSubscribeCommand.route to {
                SettingsMqttTopicsSubscribeCommandScreen(navController)
            },
            Screen.SettingsMqttTopicsSubscribeSettings.route to {
                SettingsMqttTopicsSubscribeSettingsScreen(navController)
            },
            Screen.SettingsMqttTopicsSubscribeRequest.route to {
                SettingsMqttTopicsSubscribeRequestScreen(navController)
            },
            Screen.SettingsUnifiedPush.route to {
                SettingsUnifiedPushScreen(navController)
            },
            Screen.SettingsUnifiedPushDebug.route to {
                SettingsUnifiedPushDebugScreen(navController)
            },
            Screen.SettingsAbout.route to {
                SettingsAboutScreen(navController)
            }
        )
    }

    // Preserve the graph across recomposition and when the NavHost temporarily leaves
    // composition (for example while a shared file is being imported). Replacing the
    // graph can invalidate entries still referenced by an in-flight predictive back.
    val graph = remember(navController) {
        runCatching { navController.graph }.getOrNull()
            ?: navController.createGraph(startDestination = Screen.WebView.route) {
                composable(Screen.WebView.route) { entry ->
                    val recreationRequest by entry.savedStateHandle
                        .getStateFlow(WEBVIEW_RECREATION_REQUEST_KEY, 0)
                        .collectAsState()
                    key(recreationRequest) {
                        WebviewScreen(navController)
                    }
                }
                composable(Screen.AdminRestrictionsChanged.route) {
                    AdminRestrictionsChangedScreen(navController)
                }
                navigation(startDestination = Screen.Settings.route, route = "settings_list") {
                    for ((route, content) in settingsScreens) {
                        authComposable(route, navController) {
                            content()
                        }
                    }
                }
            }
    }

    NavHost(navController = navController, graph = graph)
}

@Composable
private fun KeepLegacyInsetsListenerAttached() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        // Authentication placeholders have no inset consumers. Keep the activity listener
        // attached across navigation so legacy inset animations finish and clear their
        // cached animation state.
        // The transition hides this issue, but can be reproduced by adding the following
        // properties to NavHost:
        //     enterTransition = { EnterTransition.None },
        //     exitTransition = { ExitTransition.None },
        //     popEnterTransition = { EnterTransition.None },
        //     popExitTransition = { ExitTransition.None },
        @Suppress("Unused")
        val activityImeInsets = WindowInsets.ime
    }
}
