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
            authComposable(Screen.SettingsMqtt.route) {
                SettingsMqttScreen(navController)
            }
            authComposable(Screen.SettingsMqttConnection.route) {
                SettingsMqttConnectionScreen(navController)
            }
            authComposable(Screen.SettingsMqttWill.route) {
                SettingsMqttWillScreen(navController)
            }
            authComposable(Screen.SettingsMqttRestrictions.route) {
                SettingsMqttRestrictionsScreen(navController)
            }
            authComposable(Screen.SettingsMqttDebug.route) {
                SettingsMqttDebugScreen(navController)
            }
            authComposable(Screen.SettingsMqttTopics.route) {
                SettingsMqttTopicsScreen(navController)
            }
            authComposable(Screen.SettingsMqttTopicsPublishEvent.route) {
                SettingsMqttTopicsPublishEventScreen(navController)
            }
            authComposable(Screen.SettingsMqttTopicsPublishResponse.route) {
                SettingsMqttTopicsPublishResponseScreen(navController)
            }
            authComposable(Screen.SettingsMqttTopicsSubscribeCommand.route) {
                SettingsMqttTopicsSubscribeCommandScreen(navController)
            }
            authComposable(Screen.SettingsMqttTopicsSubscribeSettings.route) {
                SettingsMqttTopicsSubscribeSettingsScreen(navController)
            }
            authComposable(Screen.SettingsMqttTopicsSubscribeRequest.route) {
                SettingsMqttTopicsSubscribeRequestScreen(navController)
            }
            authComposable(Screen.SettingsAbout.route) {
                SettingsAboutScreen(navController)
            }
        }
    }
}
