package com.nktnet.webview_kiosk

import android.content.Intent
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
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.ui.components.KeepScreenOnOption
import com.nktnet.webview_kiosk.ui.components.auth.RequireAuthWrapper
import com.nktnet.webview_kiosk.ui.theme.WebviewKioskTheme
import com.nktnet.webview_kiosk.ui.view.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemSettings = SystemSettings(this)

        // Opening links from other apps
        if (intent.action == Intent.ACTION_VIEW && intent.data != null && systemSettings.intentUrl.isEmpty()) {
            systemSettings.intentUrl = intent?.dataString ?: ""
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
            finish()
            return
        }

        enableEdgeToEdge()
        BiometricPromptManager.init(this)
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
                            authComposable(Screen.Settings.route) {
                                SettingsListScreen(
                                    navController,
                                    themeState = themeState,
                                )
                            }
                            authComposable(Screen.SettingsWebContent.route) {
                                SettingsWebContentScreen(navController)
                            }
                            authComposable(Screen.SettingsWebBrowsing.route) {
                                SettingsWebBrowsingScreen(navController)
                            }
                            authComposable(Screen.SettingsWebEngine.route) {
                                SettingsWebEngineScreen(
                                    navController,
                                )
                            }
                            authComposable(Screen.SettingsAppearance.route) {
                                SettingsAppearanceScreen(
                                    navController,
                                    themeState = themeState,
                                )
                            }
                            authComposable(Screen.SettingsDevice.route) {
                                SettingsDeviceScreen(
                                    navController,
                                    keepScreenOnState
                                )
                            }
                            authComposable(Screen.SettingsAbout.route) {
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

    override fun onStart() {
        super.onStart()
        BiometricPromptManager.init(this)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            BiometricPromptManager.resetAuthentication()
        }
    }
}

inline fun NavGraphBuilder.authComposable(
    route: String,
    crossinline content: @Composable () -> Unit
) {
    composable(route) {
        RequireAuthWrapper {
            content()
        }
    }
}
