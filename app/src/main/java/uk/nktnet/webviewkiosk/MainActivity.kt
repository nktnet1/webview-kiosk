package uk.nktnet.webviewkiosk

import android.app.ActivityManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import uk.nktnet.webviewkiosk.auth.BiometricPromptManager
import uk.nktnet.webviewkiosk.config.*
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.ui.components.webview.KeepScreenOnOption
import uk.nktnet.webviewkiosk.ui.theme.WebviewKioskTheme
import uk.nktnet.webviewkiosk.ui.screens.*
import uk.nktnet.webviewkiosk.utils.authComposable
import uk.nktnet.webviewkiosk.utils.getWebContentFilesDir
import uk.nktnet.webviewkiosk.utils.handlePreviewKeyEvent
import uk.nktnet.webviewkiosk.utils.tryLockTask

class MainActivity : AppCompatActivity() {
    private var uploadingFileUri: Uri? = null
    private var uploadProgress by mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val systemSettings = SystemSettings(this)
        val userSettings = UserSettings(this)
        val webContentDir = getWebContentFilesDir(this)

        var toastRef: Toast? = null
        val showToast: (String) -> Unit = { msg ->
            toastRef?.cancel()
            toastRef = Toast.makeText(this, msg, Toast.LENGTH_SHORT).apply { show() }
        }

        BiometricPromptManager.init(this)
        applyDeviceRotation(userSettings.deviceRotation)
        systemSettings.isFreshLaunch = true

        handleIntent(systemSettings)

        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager

        if (userSettings.lockOnLaunch) {
            tryLockTask(this, showToast)
        }

        setContent {
            val themeState = remember { mutableStateOf(userSettings.theme) }
            val keepScreenOnState = remember { mutableStateOf(userSettings.keepScreenOn) }
            val deviceRotationState = remember { mutableStateOf(userSettings.deviceRotation) }

            KeepScreenOnOption(keepOn = keepScreenOnState.value)
            LaunchedEffect(deviceRotationState.value) { applyDeviceRotation(deviceRotationState.value) }

            val isDarkTheme = resolveTheme(themeState.value)
            val window = (this as? AppCompatActivity)?.window
            val insetsController = remember(window) { window?.let { WindowInsetsControllerCompat(it, it.decorView) } }
            LaunchedEffect(isDarkTheme) {
                insetsController?.isAppearanceLightStatusBars = !isDarkTheme
                insetsController?.isAppearanceLightNavigationBars = !isDarkTheme
            }


            WebviewKioskTheme(darkTheme = isDarkTheme) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event: KeyEvent ->
                            handlePreviewKeyEvent(this, activityManager, userSettings, event)
                        }
                ) {
                    val navController = rememberNavController()
                    uploadingFileUri?.let { uri ->
                        UploadFileProgressScreen(
                            context = this@MainActivity,
                            uri = uri,
                            targetDir = webContentDir,
                            onProgress = { progress -> uploadProgress = progress },
                            onComplete = { file ->
                                systemSettings.intentUrl = "file://${file.absolutePath}"
                                startActivity(
                                    Intent(this@MainActivity, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                )
                                finish()
                            }
                        )
                    } ?: run {
                        SetupNavHost(navController, themeState, keepScreenOnState, deviceRotationState)
                    }
                }
            }
        }
    }

    private fun handleIntent(systemSettings: SystemSettings) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { dataUri ->
                    if (dataUri.scheme == "content") {
                        uploadingFileUri = dataUri
                    } else if (systemSettings.intentUrl.isEmpty()) {
                        systemSettings.intentUrl = dataUri.toString()
                    }
                }
            }
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                } ?: intent.data

                uri?.let { uploadingFileUri = it }
            }
        }
    }

    @Composable
    private fun resolveTheme(themeOption: ThemeOption): Boolean {
        return when (themeOption) {
            ThemeOption.SYSTEM -> isSystemInDarkTheme()
            ThemeOption.DARK -> true
            ThemeOption.LIGHT -> false
        }
    }

    @Composable
    private fun SetupNavHost(
        navController: NavHostController,
        themeState: MutableState<ThemeOption>,
        keepScreenOnState: MutableState<Boolean>,
        deviceRotationState: MutableState<DeviceRotationOption>
    ) {
        NavHost(navController, startDestination = Screen.WebView.route) {
            composable(Screen.WebView.route) {
                WebviewScreen(navController)
            }

            navigation(startDestination = Screen.Settings.route, route = "settings_list") {
                authComposable(Screen.Settings.route) {
                    SettingsListScreen(navController, themeState = themeState)
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
                authComposable(Screen.SettingsAbout.route) {
                    SettingsAboutScreen(navController)
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
