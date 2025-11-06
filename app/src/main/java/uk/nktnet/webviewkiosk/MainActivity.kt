package uk.nktnet.webviewkiosk

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import uk.nktnet.webviewkiosk.auth.BiometricPromptManager
import uk.nktnet.webviewkiosk.config.*
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.mqtt.MqttManager
import uk.nktnet.webviewkiosk.handlers.backbutton.BackButtonService
import uk.nktnet.webviewkiosk.main.SetupNavHost
import uk.nktnet.webviewkiosk.main.applyDeviceRotation
import uk.nktnet.webviewkiosk.main.handleMainIntent
import uk.nktnet.webviewkiosk.states.InactivityStateSingleton
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton
import uk.nktnet.webviewkiosk.ui.components.webview.KeepScreenOnOption
import uk.nktnet.webviewkiosk.ui.placeholders.UploadFileProgress
import uk.nktnet.webviewkiosk.ui.theme.WebviewKioskTheme
import uk.nktnet.webviewkiosk.utils.getLocalUrl
import uk.nktnet.webviewkiosk.utils.getWebContentFilesDir
import uk.nktnet.webviewkiosk.utils.handlePreviewKeyUnlockEvent
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.tryUnlockTask

class MainActivity : AppCompatActivity() {
    private val navControllerState = mutableStateOf<NavHostController?>(null)
    private var uploadingFileUri: Uri? = null
    private var uploadProgress by mutableFloatStateOf(0f)
    private lateinit var subscriber: MqttManager
    private lateinit var userSettings: UserSettings
    private lateinit var themeState: MutableState<ThemeOption>
    private lateinit var keepScreenOnState: MutableState<Boolean>
    private lateinit var deviceRotationState: MutableState<DeviceRotationOption>
    private lateinit var backButtonService: BackButtonService

    val restrictionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED) {
                val currentRoute = navControllerState.value?.currentBackStackEntry?.destination?.route
                if (currentRoute != Screen.AdminRestrictionsChanged.route) {
                    navControllerState.value?.navigate(Screen.AdminRestrictionsChanged.route)
                }
                updateUserSettings()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LockStateSingleton.startMonitoring(application)
        setupLockTaskPackage(this)

        backButtonService = BackButtonService(
            lifecycleScope = lifecycleScope,
        )
        onBackPressedDispatcher.addCallback(
            this,
            backButtonService.onBackPressedCallback,
        )

        registerReceiver(
            restrictionsReceiver,
            IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
        )

        userSettings = UserSettings(this)
        themeState = mutableStateOf(userSettings.theme)
        keepScreenOnState = mutableStateOf(userSettings.keepScreenOn)
        deviceRotationState = mutableStateOf(userSettings.deviceRotation)

        val systemSettings = SystemSettings(this)
        val webContentDir = getWebContentFilesDir(this)

        var toastRef: Toast? = null
        val showToast: (String) -> Unit = { msg ->
            toastRef?.cancel()
            toastRef = Toast.makeText(
                this, msg, Toast.LENGTH_SHORT
            ).apply { show() }
        }

        BiometricPromptManager.init(this)
        applyDeviceRotation(userSettings.deviceRotation)
        systemSettings.isFreshLaunch = true

        val intentUrlResult = handleMainIntent(intent)
        if (!intentUrlResult.url.isNullOrEmpty()) {
            systemSettings.intentUrl = intentUrlResult.url
        } else {
            uploadingFileUri = intentUrlResult.uploadUri
        }

        if (userSettings.lockOnLaunch) {
            tryLockTask(this, showToast)
        }

        setContent {
            val navController = rememberNavController()
            navControllerState.value = navController

            KeepScreenOnOption(keepOn = keepScreenOnState.value)
            LaunchedEffect(deviceRotationState.value) {
                applyDeviceRotation(deviceRotationState.value)
            }

            val waitingForUnlock by WaitingForUnlockStateSingleton.waitingForUnlock.collectAsState()
            val biometricResult by BiometricPromptManager.promptResults.collectAsState()
            val activity = LocalActivity.current

            LaunchedEffect(waitingForUnlock, biometricResult) {
                if (
                    waitingForUnlock
                ) {
                    if (biometricResult == BiometricPromptManager.BiometricResult.Loading) {
                        return@LaunchedEffect
                    }
                    if (
                        biometricResult == BiometricPromptManager.BiometricResult.AuthenticationSuccess
                        || biometricResult == BiometricPromptManager.BiometricResult.AuthenticationNotSet
                    ) {
                        tryUnlockTask(activity, showToast)
                        WaitingForUnlockStateSingleton.emitUnlockSuccess()
                    }
                    WaitingForUnlockStateSingleton.stopWaiting()
                }
            }

            val isDarkTheme = resolveTheme(themeState.value)
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
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    uploadingFileUri?.let { uri ->
                        UploadFileProgress(
                            context = this@MainActivity,
                            uri = uri,
                            targetDir = webContentDir,
                            onProgress = { progress -> uploadProgress = progress },
                            onComplete = { file ->
                                systemSettings.intentUrl = file.getLocalUrl()
                                startActivity(
                                    Intent(
                                        this@MainActivity,
                                        MainActivity::class.java
                                    ).apply {
                                        flags = (
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                            or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        )
                                    }
                                )
                                finish()
                            }
                        )
                    } ?: run {
                        SetupNavHost(
                            navController, themeState, keepScreenOnState, deviceRotationState
                        )
                    }
                }
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

    private fun updateUserSettings(context: Context = this) {
        userSettings = UserSettings(context)
        themeState.value = userSettings.theme
        keepScreenOnState.value = userSettings.keepScreenOn
        deviceRotationState.value = userSettings.deviceRotation
    }

    override fun onStart() {
        super.onStart()
        if (userSettings.mqttEnabled && !MqttManager.isConnectedOrReconnect()) {
            MqttManager.connect(
                userSettings,
            )
        }
        BiometricPromptManager.init(this)
    }

    override fun onResume() {
        super.onResume()
        updateUserSettings()
        backButtonService.onBackPressedCallback.isEnabled = true
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        InactivityStateSingleton.onUserInteraction()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            BiometricPromptManager.resetAuthentication()
            MqttManager.disconnect {}
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (handlePreviewKeyUnlockEvent(this, activityManager, userSettings, event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        unregisterReceiver(restrictionsReceiver)
        subscriber.disconnect()
        super.onDestroy()
    }

    @Deprecated("For Android 5.0 (SDK 21-22)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            BiometricPromptManager.handleLollipopDeviceCredentialResult(requestCode, resultCode)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) =
        backButtonService.onKeyDown(keyCode) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent) =
        backButtonService.onKeyUp(keyCode) || super.onKeyUp(keyCode, event)

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent) =
        backButtonService.onKeyLongPress(keyCode) || super.onKeyLongPress(keyCode, event)
}
