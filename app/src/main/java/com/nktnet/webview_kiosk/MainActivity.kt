package com.nktnet.webview_kiosk

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.nktnet.webview_kiosk.auth.BiometricPromptManager
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.ui.components.KeepScreenOnOption
import com.nktnet.webview_kiosk.ui.components.auth.RequireAuthWrapper
import com.nktnet.webview_kiosk.ui.theme.WebviewKioskTheme
import com.nktnet.webview_kiosk.ui.view.*

class MainActivity : AppCompatActivity() {
    private val promptManager by lazy {
        BiometricPromptManager(this)
    }

    override fun onStop() {
        super.onStop()
        promptManager.resetAuthentication()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val userSettings = UserSettings(this)

        setContent {
            val themeState = remember { mutableStateOf(userSettings.theme) }
            val keepScreenOnState = remember { mutableStateOf(userSettings.keepScreenOn) }

            KeepScreenOnOption(keepOn = keepScreenOnState.value)

            val isDarkTheme = when (themeState.value) {
                ThemeOption.SYSTEM -> isSystemInDarkTheme()
                ThemeOption.DARK -> true
                ThemeOption.LIGHT -> false
            }

            val window = (this as? AppCompatActivity)?.window
            val insetsController = remember(window) {
                window?.let { WindowInsetsControllerCompat(it, it.decorView) }
            }

            LaunchedEffect(isDarkTheme) {
                insetsController?.isAppearanceLightStatusBars = !isDarkTheme
                insetsController?.isAppearanceLightNavigationBars = !isDarkTheme
            }

            WebviewKioskTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController, startDestination = Screen.WebView.route) {
                        composable(Screen.WebView.route) {
                            WebviewScreen(navController)
                        }

                        navigation(
                            startDestination = Screen.Settings.route,
                            route = "settings_list"
                        ) {
                            authComposable(Screen.Settings.route, promptManager) {
                                SettingsListScreen(
                                    navController,
                                    themeState = themeState,
                                )
                            }
                            authComposable(Screen.SettingsAppearance.route, promptManager) {
                                SettingsAppearanceScreen(
                                    navController,
                                    themeState = themeState,
                                )
                            }
                            authComposable(Screen.SettingsWebContent.route, promptManager) {
                                SettingsWebContentScreen(navController)
                            }
                            authComposable(Screen.SettingsWebBrowsing.route, promptManager) {
                                SettingsWebBrowsingScreen(navController)
                            }
                            authComposable(Screen.SettingsDevice.route, promptManager) {
                                SettingsDeviceScreen(
                                    navController,
                                    keepScreenOnState
                                )
                            }
                            authComposable(Screen.SettingsAbout.route, promptManager) {
                                SettingsAboutScreen(
                                    navController,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

inline fun NavGraphBuilder.authComposable(
    route: String,
    promptManager: BiometricPromptManager,
    crossinline content: @Composable () -> Unit
) {
    composable(route) {
        RequireAuthWrapper(promptManager) {
            content()
        }
    }
}
