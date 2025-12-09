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
    NavHost(navController, startDestination = Screen.WebView.route) {
        composable(Screen.WebView.route) {
            WebviewScreen(navController)
        }
        composable(Screen.AdminRestrictionsChanged.route) {
            AdminRestrictionsChangedScreen(navController)
        }

        navigation(startDestination = Screen.Settings.route, route = "settings_list") {
            authComposable(Screen.Settings.route, navController) {
                SettingsListScreen(navController)
            }
            authComposable(Screen.SettingsMoreActions.route, navController) {
                SettingsMoreActionsScreen(navController)
            }
            authComposable(Screen.SettingsWebContent.route, navController) {
                SettingsWebContentScreen(navController)
            }
            authComposable(Screen.SettingsWebContentFiles.route, navController) {
                SettingsWebContentFilesScreen(navController)
            }
            authComposable(Screen.SettingsWebBrowsing.route, navController) {
                SettingsWebBrowsingScreen(navController)
            }
            authComposable(Screen.SettingsWebBrowsingSitePermissions.route, navController) {
                SettingsWebBrowsingSitePermissionsScreen(navController)
            }
            authComposable(Screen.SettingsWebEngine.route, navController) {
                SettingsWebEngineScreen(navController)
            }
            authComposable(Screen.SettingsWebLifecycle.route, navController) {
                SettingsWebLifecycleScreen(navController)
            }
            authComposable(Screen.SettingsAppearance.route, navController) {
                SettingsAppearanceScreen(navController)
            }
            authComposable(Screen.SettingsDevice.route, navController) {
                SettingsDeviceScreen(navController)
            }
            authComposable(Screen.SettingsDeviceOwner.route, navController) {
                SettingsDeviceOwnerScreen(navController)
            }
            authComposable(Screen.SettingsJsScript.route, navController) {
                SettingsJsScriptsScreen(navController)
            }
            authComposable(Screen.SettingsAbout.route, navController) {
                SettingsAboutScreen(navController)
            }
        }
    }
}
