package com.nktnet.webview_kiosk

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.nktnet.webview_kiosk.auth.BiometricPromptManager
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.ui.components.AuthenticationErrorDisplay
import com.nktnet.webview_kiosk.ui.components.RequireAuthentication
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
        setContent {
            WebviewKioskTheme {
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
                                SettingsListScreen(navController)
                            }
                        }
                        composable(Screen.SettingsAppearance.route) {
                            RequireAuthWrapper(promptManager) {
                                SettingsAppearanceScreen(navController)
                            }
                        }
                        composable(Screen.SettingsUrlControl.route) {
                            RequireAuthWrapper(promptManager) {
                                SettingsUrlControlScreen(navController)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RequireAuthWrapper(
    promptManager: BiometricPromptManager,
    content: @Composable () -> Unit
) {
    RequireAuthentication(
        promptManager = promptManager,
        onAuthenticated = { content() },
        onFailed = { errorResult ->
            AuthenticationErrorDisplay(errorResult = errorResult) {
                promptManager.showBiometricPrompt(
                    title = "Authentication Required",
                    description = "Please authenticate to access settings"
                )
            }
        }
    )
}
