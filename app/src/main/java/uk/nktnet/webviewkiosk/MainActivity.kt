package uk.nktnet.webviewkiosk

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
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
import uk.nktnet.webviewkiosk.auth.BiometricPromptManager
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.ui.components.webview.KeepScreenOnOption
import uk.nktnet.webviewkiosk.ui.components.auth.RequireAuthWrapper
import uk.nktnet.webviewkiosk.ui.theme.WebviewKioskTheme
import uk.nktnet.webviewkiosk.ui.view.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemSettings = SystemSettings(this)
        val userSettings = UserSettings(this)

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

        applyDeviceRotation(userSettings.deviceRotation)

        setContent {
            val themeState = remember { mutableStateOf(userSettings.theme) }
            val keepScreenOnState = remember { mutableStateOf(userSettings.keepScreenOn) }
            val deviceRotationState = remember { mutableStateOf(userSettings.deviceRotation) }

            KeepScreenOnOption(keepOn = keepScreenOnState.value)

            LaunchedEffect(deviceRotationState.value) {
                applyDeviceRotation(deviceRotationState.value)
            }

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
                                    keepScreenOnState,
                                    deviceRotationState
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

    private fun applyDeviceRotation(rotation: DeviceRotationOption) {
        requestedOrientation = when (rotation) {
            DeviceRotationOption.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            DeviceRotationOption.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DeviceRotationOption.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            DeviceRotationOption.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            DeviceRotationOption.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            BiometricPromptManager.handleLollipopDeviceCredentialResult(requestCode, resultCode)
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
