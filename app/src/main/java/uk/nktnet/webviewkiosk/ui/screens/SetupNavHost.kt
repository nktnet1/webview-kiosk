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
            authComposable(Screen.Settings.route) {
                SettingsListScreen(navController)
            }
            authComposable(Screen.SettingsMoreActions.route) {
                SettingsMoreActionsScreen(navController)
            }
            authComposable(Screen.SettingsWebContent.route) {
                SettingsWebContentScreen(navController)
            }
            authComposable(Screen.SettingsWebContentFiles.route) {
                SettingsWebContentFilesScreen(navController)
            }
            authComposable(Screen.SettingsWebBrowsing.route) {
                SettingsWebBrowsingScreen(navController)
            }
            authComposable(Screen.SettingsWebBrowsingSitePermissions.route) {
                SettingsWebBrowsingSitePermissionsScreen(navController)
            }
            authComposable(Screen.SettingsWebEngine.route) {
                SettingsWebEngineScreen(navController)
            }
            authComposable(Screen.SettingsWebLifecycle.route) {
                SettingsWebLifecycleScreen(navController)
            }
            authComposable(Screen.SettingsAppearance.route) {
                SettingsAppearanceScreen(navController)
            }
            authComposable(Screen.SettingsDevice.route) {
                SettingsDeviceScreen(navController)
            }
            authComposable(Screen.SettingsDeviceOwner.route) {
                SettingsDeviceOwnerScreen(navController)
            }
            authComposable(Screen.SettingsJsScript.route) {
                SettingsJsScriptsScreen(navController)
            }
            authComposable(Screen.SettingsAbout.route) {
                SettingsAboutScreen(navController)
            }
        }
    }
}
