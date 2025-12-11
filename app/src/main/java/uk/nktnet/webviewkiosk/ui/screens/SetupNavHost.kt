package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.utils.authComposable

@Composable
fun SetupNavHost(
    navController: NavHostController,
) {
    val settingsScreens: List<Pair<String, @Composable () -> Unit>> = listOf(
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
        Screen.SettingsAbout.route to { SettingsAboutScreen(navController) }
    )

    NavHost(navController, startDestination = Screen.WebView.route) {
        composable(Screen.WebView.route) {
            WebviewScreen(navController)
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
