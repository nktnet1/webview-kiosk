package uk.nktnet.webviewkiosk.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.ui.screens.*
import uk.nktnet.webviewkiosk.utils.authComposable

@Composable
fun SetupNavHost(
    navController: NavHostController,
    themeState: MutableState<ThemeOption>,
    keepScreenOnState: MutableState<Boolean>,
    deviceRotationState: MutableState<DeviceRotationOption>
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
                SettingsListScreen(navController, themeState = themeState)
            }
            authComposable(Screen.SettingsMoreActions.route) {
                MoreActionsScreen(navController)
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
                SettingsAppearanceScreen(navController, themeState = themeState)
            }
            authComposable(Screen.SettingsDevice.route) {
                SettingsDeviceScreen(navController, keepScreenOnState, deviceRotationState)
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
            authComposable(Screen.SettingsMqttDebug.route) {
                SettingsMqttDebugScreen(navController)
            }
            authComposable(Screen.SettingsMqttTopics.route) {
                SettingsMqttTopicsScreen(navController)
            }
            authComposable(Screen.SettingsMqttTopicsPublishEvent.route) {
                SettingsMqttTopicsPublishEventScreen(navController)
            }
            authComposable(Screen.SettingsMqttTopicsSubscribeCommand.route) {
                SettingsMqttTopicsSubscribeCommandScreen(navController)
            }
            authComposable(Screen.SettingsMqttTopicsSubscribeSettings.route) {
                SettingsMqttTopicsSubscribeSettingsScreen(navController)
            }
            authComposable(Screen.SettingsAbout.route) {
                SettingsAboutScreen(navController)
            }
        }
    }
}
