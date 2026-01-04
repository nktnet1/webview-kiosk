package uk.nktnet.webviewkiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttDisconnectingEvent
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.handlers.MqttHandler
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.managers.BackButtonManager
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.services.MqttForegroundService
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.states.ThemeStateSingleton
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton
import uk.nktnet.webviewkiosk.ui.components.auth.CustomAuthPasswordDialog
import uk.nktnet.webviewkiosk.ui.components.webview.KeepScreenOnOption
import uk.nktnet.webviewkiosk.ui.placeholders.UploadFileProgress
import uk.nktnet.webviewkiosk.ui.screens.SetupNavHost
import uk.nktnet.webviewkiosk.ui.theme.WebviewKioskTheme
import uk.nktnet.webviewkiosk.utils.getLocalUrl
import uk.nktnet.webviewkiosk.utils.getWebContentFilesDir
import uk.nktnet.webviewkiosk.utils.handleKeyEvent
import uk.nktnet.webviewkiosk.utils.handleMainIntent
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.tryUnlockTask
import uk.nktnet.webviewkiosk.utils.updateDeviceSettings

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavHostController
    private var uploadingFileUri by mutableStateOf<Uri?>(null)
    private var uploadProgress by mutableFloatStateOf(0f)
    private lateinit var userSettings: UserSettings
    private lateinit var systemSettings: SystemSettings
    private lateinit var backButtonService: BackButtonManager

    private var lastOnStartTime = 0L

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED -> {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute != Screen.AdminRestrictionsChanged.route) {
                        navController.navigate(Screen.AdminRestrictionsChanged.route)
                    }
                    updateDeviceSettings(context)
                    AuthenticationManager.resetAuthentication()
                    AuthenticationManager.hideCustomAuthPrompt()
                    MqttManager.publishApplicationRestrictionsChangedEvent()
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    MqttManager.publishPowerPluggedEvent()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    MqttManager.publishPowerUnpluggedEvent()
                }
                else -> Unit
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CustomNotificationManager.init(applicationContext)
        userSettings = UserSettings(this)
        systemSettings = SystemSettings(this)
        DeviceOwnerManager.init(this)
        // https://github.com/nktnet1/webview-kiosk/pull/195
        getExternalFilesDir(null)

        if (DeviceOwnerManager.status.value.mode == DeviceOwnerMode.DeviceOwner) {
            setupLockTaskPackage(this)
        } else if (
            DeviceOwnerManager.status.value.mode == DeviceOwnerMode.Dhizuku
            && userSettings.dhizukuRequestPermissionOnLaunch
        ) {
            lifecycleScope.launch {
                delay(1000)
                DeviceOwnerManager.requestDhizukuPermission(
                    onGranted = {
                        setupLockTaskPackage(this@MainActivity)
                    }
                )
            }
        }

        LockStateSingleton.startMonitoring(application)

        backButtonService = BackButtonManager(
            lifecycleScope = lifecycleScope,
        )
        onBackPressedDispatcher.addCallback(
            this,
            backButtonService.onBackPressedCallback,
        )

        registerReceiver(
            broadcastReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        )

        MqttManager.updateConfig(this)

        val webContentDir = getWebContentFilesDir(this)

        AuthenticationManager.init(this)

        systemSettings.isFreshLaunch = true

        if (userSettings.lockOnLaunch) {
            tryLockTask(this)
        }

        if (intent != null) {
            saveIntentUrl(intent)
        }

        setContent {
            navController = rememberNavController()

            KeepScreenOnOption()

            val waitingForUnlock by WaitingForUnlockStateSingleton.waitingForUnlock.collectAsState()
            val biometricResult by AuthenticationManager.promptResults.collectAsState()
            val context = LocalContext.current

            val activity = LocalActivity.current

            LaunchedEffect(Unit) {
                MqttManager.commands.collect { command ->
                    if (!userSettings.mqttUseForegroundService) {
                        MqttHandler.handleMqttCommand(context, command)
                    }
                }
            }

            LaunchedEffect(Unit) {
                MqttManager.requests.collect { request ->
                    if (!userSettings.mqttUseForegroundService) {
                        MqttHandler.handleMqttRequest(context, request)
                    }
                }
            }

            LaunchedEffect(Unit) {
                MqttManager.settings.collect { settings ->
                    if (!userSettings.mqttUseForegroundService) {
                        MqttHandler.handleMqttSettings(context, settings)
                    }

                    // Counterintuitive, but this acts as a "Refresh" of the webview screen,
                    // which will recreate + apply settings.
                    // If we're on another screen though (e.g. settings), then let the user
                    // decide when to navigate back.
                    if (
                        settings.reloadActivity
                        && navController.currentDestination?.route == Screen.WebView.route
                    ) {
                        navigateToWebViewScreen(navController)
                    }
                }
            }

            LaunchedEffect(waitingForUnlock, biometricResult) {
                if (
                    waitingForUnlock
                ) {
                    if (biometricResult == AuthenticationManager.AuthenticationResult.Loading) {
                        return@LaunchedEffect
                    }
                    if (
                        biometricResult == AuthenticationManager.AuthenticationResult.AuthenticationSuccess
                        || biometricResult == AuthenticationManager.AuthenticationResult.AuthenticationNotSet
                    ) {
                        tryUnlockTask(activity)
                        WaitingForUnlockStateSingleton.emitUnlockSuccess()
                    }
                    WaitingForUnlockStateSingleton.stopWaiting()
                }
            }

            val isDarkTheme = resolveTheme(ThemeStateSingleton.currentTheme.value)
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
                                uploadingFileUri = null
                            }
                        )
                    } ?: run {
                        CustomAuthPasswordDialog()
                        SetupNavHost(navController)
                    }
                }
            }
        }
    }

    @Composable
    private fun resolveTheme(theme: ThemeOption): Boolean {
        return when (theme) {
            ThemeOption.SYSTEM -> isSystemInDarkTheme()
            ThemeOption.DARK -> true
            ThemeOption.LIGHT -> false
        }
    }

    private fun saveIntentUrl(intent: Intent): Boolean {
        val intentUrlResult = handleMainIntent(intent)
        if (!intentUrlResult.url.isNullOrEmpty()) {
            systemSettings.intentUrl = intentUrlResult.url
            return true
        } else if (intentUrlResult.uploadUri != null) {
            uploadingFileUri = intentUrlResult.uploadUri
            return true
        }
        return false
    }

    override fun onStart() {
        super.onStart()
        lastOnStartTime = System.currentTimeMillis()
        AuthenticationManager.init(this)
        DeviceOwnerManager.init(this)
        updateDeviceSettings(this)
        if (
            userSettings.mqttEnabled
        ) {
            if (!MqttManager.isConnectedOrReconnect()) {
                MqttManager.connect(applicationContext)
            }
            if (userSettings.mqttUseForegroundService && MqttManager.isConnected()) {
                MqttManager.publishAppForegroundEvent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        backButtonService.onBackPressedCallback.isEnabled = true
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        UserInteractionStateSingleton.onUserInteraction()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            AuthenticationManager.resetAuthentication()
            if (MqttManager.isConnected()) {
                if (userSettings.mqttUseForegroundService) {
                    MqttManager.publishAppBackgroundEvent()
                } else {
                    MqttManager.disconnect(
                        cause = MqttDisconnectingEvent.DisconnectCause.SYSTEM_ACTIVITY_STOPPED
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!this::navController.isInitialized) {
            return
        }
        if (
            intent.getBooleanExtra(
                Constants.INTENT_NAVIGATE_TO_WEBVIEW_SCREEN,
                false
            )
        ) {
            if (callingPackage == packageName) {
                navigateToWebViewScreen(navController)
            }
            return
        }
        if (
            System.currentTimeMillis() - lastOnStartTime > 100L
            && intent.action == Intent.ACTION_MAIN
            && intent.hasCategory(Intent.CATEGORY_HOME)
            && userSettings.allowGoHome
        ) {
            UserInteractionStateSingleton.onUserInteraction()
            systemSettings.intentUrl = userSettings.homeUrl
            navigateToWebViewScreen(navController)
            return
        }
        val hasIntentUrl = saveIntentUrl(intent)
        if (hasIntentUrl) {
            navigateToWebViewScreen(navController)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleKeyEvent(this, event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        unregisterReceiver(broadcastReceiver)
        if (
            userSettings.mqttUseForegroundService
            && MqttManager.isConnected()
        ) {
            MqttManager.disconnect(
                cause = MqttDisconnectingEvent.DisconnectCause.SYSTEM_ACTIVITY_DESTROYED
            )
        }
        stopService(
            Intent(this, MqttForegroundService::class.java)
        )
        super.onDestroy()
    }

    @Deprecated("For Android 5.0 (SDK 21-22)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            AuthenticationManager.handleLollipopDeviceCredentialResult(requestCode, resultCode)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return (
            handleKeyEvent(this, event)
            || backButtonService.onKeyDown(keyCode)
            || super.onKeyDown(keyCode, event)
        )
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return (
            handleKeyEvent(this, event)
            || backButtonService.onKeyUp(keyCode)
            || super.onKeyUp(keyCode, event)
        )
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        return (
            backButtonService.onKeyLongPress(keyCode)
            || super.onKeyLongPress(keyCode, event)
        )
    }
}
