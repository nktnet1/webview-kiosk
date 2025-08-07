package com.nktnet.webview_kiosk

import com.nktnet.webview_kiosk.ui.components.KeepScreenOnOption
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.nktnet.webview_kiosk.auth.BiometricPromptManager
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.config.UserSettings
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val userSettings = UserSettings(this)

        setContent {
            val themeState = remember { mutableStateOf(userSettings.theme) }
            val keepScreenOnState = remember { mutableStateOf(userSettings.keepScreenOn) }

            KeepScreenOnOption(keepOn = keepScreenOnState.value)

            WebviewKioskTheme(darkTheme = when (themeState.value) {
                ThemeOption.SYSTEM -> isSystemInDarkTheme()
                ThemeOption.DARK -> true
                ThemeOption.LIGHT -> false
            }) {
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
                            composable(Screen.Settings.route) {
                                RequireAuthWrapper(promptManager) {
                                    SettingsListScreen(
                                        navController,
                                        themeState = themeState,
                                    )
                                }
                            }
                            composable(Screen.SettingsAppearance.route) {
                                RequireAuthWrapper(promptManager) {
                                    SettingsAppearanceScreen(
                                        navController,
                                        themeState = themeState,
                                    )
                                }
                            }
                            composable(Screen.SettingsUrlControl.route) {
                                RequireAuthWrapper(promptManager) {
                                    SettingsUrlControlScreen(navController)
                                }
                            }
                            composable(Screen.SettingsDevice.route) {
                                RequireAuthWrapper(promptManager) {
                                    SettingsDeviceScreen(
                                        navController,
                                        keepScreenOnState
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

