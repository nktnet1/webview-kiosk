package com.nktnet.webview_kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.nktnet.webview_kiosk.utils.getStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.config.*
import com.nktnet.webview_kiosk.config.data.DeviceOwnerMode
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.managers.MqttManager
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttClearHistoryCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttDisconnectingEvent
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttErrorRequest
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttLockDeviceCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttNotifyCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttReconnectCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttSettingsRequest
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttStatusRequest
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttSystemInfoRequest
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttToastCommand
import com.nktnet.webview_kiosk.managers.AuthenticationManager
import com.nktnet.webview_kiosk.managers.BackButtonManager
import com.nktnet.webview_kiosk.managers.DeviceOwnerManager
import com.nktnet.webview_kiosk.managers.CustomNotificationManager
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.services.MqttForegroundService
import com.nktnet.webview_kiosk.ui.screens.SetupNavHost
import com.nktnet.webview_kiosk.utils.handleMainIntent
import com.nktnet.webview_kiosk.states.UserInteractionStateSingleton
import com.nktnet.webview_kiosk.states.LockStateSingleton
import com.nktnet.webview_kiosk.states.ThemeStateSingleton
import com.nktnet.webview_kiosk.states.WaitingForUnlockStateSingleton
import com.nktnet.webview_kiosk.ui.components.auth.CustomAuthPasswordDialog
import com.nktnet.webview_kiosk.ui.components.webview.KeepScreenOnOption
import com.nktnet.webview_kiosk.ui.placeholders.UploadFileProgress
import com.nktnet.webview_kiosk.ui.theme.WebviewKioskTheme
import com.nktnet.webview_kiosk.utils.getLocalUrl
import com.nktnet.webview_kiosk.utils.getSystemInfo
import com.nktnet.webview_kiosk.utils.getWebContentFilesDir
import com.nktnet.webview_kiosk.utils.handleKeyEvent
import com.nktnet.webview_kiosk.utils.navigateToWebViewScreen
import com.nktnet.webview_kiosk.utils.setupLockTaskPackage
import com.nktnet.webview_kiosk.utils.tryLockTask
import com.nktnet.webview_kiosk.utils.tryUnlockTask
import com.nktnet.webview_kiosk.utils.updateDeviceSettings
import com.nktnet.webview_kiosk.utils.wakeScreen
import com.nktnet.webview_kiosk.utils.webview.WebViewNavigation

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
                    if (command.interact) {
                        UserInteractionStateSingleton.onUserInteraction()
                    }
                    if (command.wakeScreen) {
                        wakeScreen(context)
                    }
                    when (command) {
                        is MqttReconnectCommand -> {
                            MqttManager.disconnect (
                                cause = MqttDisconnectingEvent.DisconnectCause.MQTT_RECONNECT_COMMAND_RECEIVED,
                                onDisconnected = {
                                    MqttManager.connect(applicationContext)
                                }
                            )
                        }
                        is MqttClearHistoryCommand -> {
                            WebViewNavigation.clearHistory(systemSettings)
                        }
                        is MqttToastCommand -> {
                            if (!command.data?.message.isNullOrEmpty()) {
                                ToastManager.show(context, command.data.message)
                            }
                        }
                        is MqttLockDeviceCommand -> {
                            if (DeviceOwnerManager.hasOwnerPermission(context)) {
                                try {
                                    DeviceOwnerManager.DPM.lockNow()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    ToastManager.show(
                                        context,
                                        "Failed to lock device: ${e.message}"
                                    )
                                }
                            }
                        }
                        is MqttNotifyCommand -> {
                            if (userSettings.allowNotifications) {
                                CustomNotificationManager.sendMqttNotifyCommandNotification(
                                    context,
                                    command,
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }

            LaunchedEffect(Unit) {
                MqttManager.settings.collect { settingsMessage ->
                    userSettings.importJson(settingsMessage.data.settings)

                    if (settingsMessage.reloadActivity) {
                        updateDeviceSettings(context)
                    }

                    // Counterintuitive, but this acts as a "Refresh" of the webview screen,
                    // which will recreate + apply settings.
                    // If we're on another screen though (e.g. settings), then let the user
                    // decide when to navigate back.
                    if (
                        settingsMessage.reloadActivity
                        && navController.currentDestination?.route == Screen.WebView.route
                    ) {
                        navigateToWebViewScreen(navController)
                        if (settingsMessage.showToast) {
                            ToastManager.show(context, "MQTT: settings applied.")
                        }
                    } else {
                        if (settingsMessage.showToast) {
                            ToastManager.show(context, "MQTT: settings received.")
                        }
                    }

                }
            }

            LaunchedEffect(Unit) {
                MqttManager.requests.collect { request ->
                    when (request) {
                        is MqttStatusRequest -> {
                            MqttManager.publishStatusResponse(
                                request, getStatus(context)
                            )
                        }
                        is MqttSettingsRequest -> {
                            val settings = userSettings.exportJson()
                            MqttManager.publishSettingsResponse(request, settings)
                        }
                        is MqttSystemInfoRequest -> {
                            MqttManager.publishSystemInfoResponse(
                                request, getSystemInfo(context)
                            )
                        }
                        is MqttErrorRequest -> {
                            ToastManager.show(context, "MQTT: invalid request. See debug logs.")
                            MqttManager.publishErrorResponse(request)
                        }
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
