package uk.nktnet.webviewkiosk

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent
import uk.nktnet.webviewkiosk.handlers.RemoteInboundHandler
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.managers.BackButtonManager
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
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
import uk.nktnet.webviewkiosk.utils.getLocalFileLink
import uk.nktnet.webviewkiosk.utils.getWebContentFilesDir
import uk.nktnet.webviewkiosk.utils.handleKeyEvent
import uk.nktnet.webviewkiosk.utils.handleMainIntent
import uk.nktnet.webviewkiosk.utils.launchAndroid6KioskTask
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.tryUnlockTask
import uk.nktnet.webviewkiosk.utils.updateDeviceSettings
import uk.nktnet.webviewkiosk.utils.webview.NfcBridgeManager
import uk.nktnet.webviewkiosk.utils.webview.getNfcAdapterOrNull
import kotlin.time.Duration.Companion.milliseconds

open class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavHostController
    private var uploadingFileUri by mutableStateOf<Uri?>(null)
    private var uploadProgress by mutableFloatStateOf(0f)
    private lateinit var userSettings: UserSettings
    private lateinit var systemSettings: SystemSettings
    private lateinit var backButtonService: BackButtonManager
    private var lastOnStartTime = 0L
    private var pendingAndroid6HomeRedirect = false
    private var pendingAndroid6LockRequest = false
    private var pendingDhizukuPermissionRequest = false
    private var deviceOwnerInitJob: Job? = null

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED -> {
                    if (this@MainActivity::navController.isInitialized) {
                        val currentRoute = navController.currentBackStackEntry?.destination?.route
                        if (currentRoute != Screen.AdminRestrictionsChanged.route) {
                            navController.navigate(Screen.AdminRestrictionsChanged.route)
                        }
                    }
                    updateDeviceSettings(context)
                    userSettings.refreshMutualTlsState()
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            // On API 23, enableEdgeToEdge() uses legacy systemUiVisibility layout flags.
            // AppCompat can overwrite those flags while creating the decor view, leaving the
            // content already inset by the framework before Compose applies its own insets.
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        CustomNotificationManager.init(applicationContext)
        userSettings = UserSettings(this)
        systemSettings = SystemSettings(this)
        DeviceOwnerManager.init(this)
        pendingDhizukuPermissionRequest = userSettings.dhizukuRequestPermissionOnLaunch
        // https://github.com/nktnet1/webview-kiosk/pull/195
        getExternalFilesDir(null)

        if (DeviceOwnerManager.status.value.mode == DeviceOwnerMode.DeviceOwner) {
            setupLockTaskPackage(this)
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

        if (!MqttManager.isInitialized()) {
            MqttManager.updateConfig(applicationContext)
        }

        val webContentDir = getWebContentFilesDir(this)

        AuthenticationManager.init(this)

        systemSettings.isFreshLaunch = true

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            // API 23 must enter lock task only after the activity is resumed. Keep these as
            // one-shot launch actions so returning HOME after an unlock does not lock again.
            val lockRequested = consumeAndroid6LockRequest(intent)
            pendingAndroid6HomeRedirect =
                javaClass == MainActivity::class.java && isAndroid6HomeIntent(intent)
            pendingAndroid6LockRequest =
                !pendingAndroid6HomeRedirect && (userSettings.lockOnLaunch || lockRequested)
        } else if (userSettings.lockOnLaunch) {
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
            val handlesAuthentication = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                // The API 23 workaround keeps both MainActivity and Android6KioskActivity alive.
                // Only the resumed host may display or consume the process-wide auth prompt.
                val lifecycleState by lifecycle.currentStateFlow.collectAsState()
                lifecycleState == Lifecycle.State.RESUMED
            } else {
                true
            }

            LaunchedEffect(Unit) {
                RemoteMessageManager.commandsFlow.collect { command ->
                    if (
                        command.source == RemoteMessageManager.RemoteMessage.Source.MQTT
                        && !userSettings.mqttUseForegroundService
                    ) {
                        RemoteInboundHandler.handleInboundCommand(context, command.message)
                    }
                }
            }

            LaunchedEffect(Unit) {
                RemoteMessageManager.requestsFlow.collect { request ->
                    if (
                        request.source == RemoteMessageManager.RemoteMessage.Source.MQTT
                        && !userSettings.mqttUseForegroundService
                    ) {
                        RemoteInboundHandler.handleInboundMqttRequest(context, request.message)
                    }
                }
            }

            LaunchedEffect(Unit) {
                RemoteMessageManager.settingsFlow.collect { settings ->
                    if (
                        settings.source == RemoteMessageManager.RemoteMessage.Source.MQTT
                        && !userSettings.mqttUseForegroundService
                        && settings.tryClaim()
                    ) {
                        RemoteInboundHandler.handleInboundSettings(
                            context, settings.message, settings.source
                        )
                    }
                }
            }

            LaunchedEffect(Unit) {
                RemoteMessageManager.settingsAppliedFlow.collect { settings ->
                    if (settings.message.reloadActivity) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            delay(100.milliseconds)
                            if (settings.message.reloadActivity) {
                                updateDeviceSettings(context)
                            }
                        }

                        // Counterintuitive, but this acts as a "Refresh" of the webview screen,
                        // which will recreate + apply settings.
                        // If we're on another screen though (e.g. settings), then let the user
                        // decide when to navigate back.
                        if (navController.currentDestination?.route == Screen.WebView.route) {
                            navigateToWebViewScreen(navController)
                        }
                    }
                }
            }

            LaunchedEffect(waitingForUnlock, biometricResult, handlesAuthentication) {
                val isAndroid6 = Build.VERSION.SDK_INT == Build.VERSION_CODES.M
                if (
                    isAndroid6 && (
                        !handlesAuthentication
                            || lifecycle.currentState != Lifecycle.State.RESUMED
                            || !WaitingForUnlockStateSingleton.waitingForUnlock.value
                    )
                ) {
                    return@LaunchedEffect
                }
                if (waitingForUnlock) {
                    if (
                        biometricResult == AuthenticationManager.AuthenticationResult.Loading
                        || biometricResult == AuthenticationManager.AuthenticationResult.Pending
                    ) {
                        return@LaunchedEffect
                    }
                    if (isAndroid6) {
                        // Consume the result before emitting a suspending event. A second host
                        // must not process the same unlock if activity lifecycles change.
                        WaitingForUnlockStateSingleton.stopWaiting()
                    }
                    if (
                        biometricResult == AuthenticationManager.AuthenticationResult.AuthenticationSuccess
                        || biometricResult == AuthenticationManager.AuthenticationResult.AuthenticationNotSet
                    ) {
                        val unlocked = tryUnlockTask(activity)
                        if (isAndroid6) {
                            if (unlocked) {
                                // stopWaiting changes this effect's key. Send the notification
                                // in the activity scope so that recomposition cannot cancel it.
                                lifecycleScope.launch {
                                    WaitingForUnlockStateSingleton.emitUnlockSuccess()
                                }
                            }
                        } else {
                            WaitingForUnlockStateSingleton.emitUnlockSuccess()
                        }
                    }
                    if (!isAndroid6) {
                        WaitingForUnlockStateSingleton.stopWaiting()
                    }
                }
            }

            val isDarkTheme = resolveTheme(ThemeStateSingleton.currentTheme.value)
            val window = (this as? AppCompatActivity)?.window
            val insetsController = remember(window) {
                window?.let {
                    WindowInsetsControllerCompat(it, it.decorView)
                }
            }

            LaunchedEffect(isDarkTheme) {
                insetsController?.isAppearanceLightStatusBars = !isDarkTheme
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    insetsController?.isAppearanceLightNavigationBars = !isDarkTheme
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    window?.run {
                        addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

                        // API 21-22 cannot render dark status-bar icons, so a white status bar
                        // would make the fixed light icons unreadable in light theme.
                        @Suppress("DEPRECATION")
                        statusBarColor = if (
                            !isDarkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ) {
                            Color.WHITE
                        } else if (isDarkTheme) {
                            Color.BLACK
                        } else {
                            Color.argb(0x80, 0x1B, 0x1B, 0x1B)
                        }

                        // Dark navigation-bar icons were added in API 26. Keep a dark scrim on
                        // API 21-25 in light theme so the fixed light navigation buttons remain
                        // visible, while API 26+ retains the original light/dark bar colours.
                        @Suppress("DEPRECATION")
                        navigationBarColor = if (isDarkTheme) {
                            Color.BLACK
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Color.WHITE
                        } else {
                            Color.argb(0x80, 0x1B, 0x1B, 0x1B)
                        }
                    }
                }
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
                                systemSettings.intentUrl = file.getLocalFileLink()
                                uploadingFileUri = null
                            }
                        )
                    } ?: run {
                        if (handlesAuthentication) {
                            CustomAuthPasswordDialog()
                        }
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
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            && DeviceOwnerManager.status.value.mode != DeviceOwnerMode.DeviceOwner
        ) {
            deviceOwnerInitJob?.cancel()
            deviceOwnerInitJob = lifecycleScope.launch {
                val mode = DeviceOwnerManager.initWithDhizukuRetry(this@MainActivity)
                if (mode == DeviceOwnerMode.Dhizuku) {
                    updateDeviceSettings(this@MainActivity)
                    if (pendingDhizukuPermissionRequest) {
                        pendingDhizukuPermissionRequest = false
                        DeviceOwnerManager.requestDhizukuPermission(
                            context = this@MainActivity,
                            onGranted = {
                                setupLockTaskPackage(this@MainActivity)
                            }
                        )
                    }
                }
            }
        }
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
        enableNfcForegroundDispatch()

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            if (pendingAndroid6HomeRedirect) {
                pendingAndroid6HomeRedirect = false
                launchAndroid6KioskTask(this, fromHome = true)
                return
            }

            if (pendingAndroid6LockRequest) {
                pendingAndroid6LockRequest = false
                tryLockTask(this)
            }
        }
    }

    override fun onPause() {
        disableNfcForegroundDispatch()
        super.onPause()
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
                        cause = OutboundDisconnectingEvent.DisconnectCause.SYSTEM_ACTIVITY_STOPPED
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            setIntent(intent)
            if (javaClass == MainActivity::class.java && isAndroid6HomeIntent(intent)) {
                // Always route HOME back to the private task. Reusing that task must not
                // request a new lock after the user has deliberately unlocked it.
                pendingAndroid6HomeRedirect = true
                pendingAndroid6LockRequest = false
                return
            }
            if (consumeAndroid6LockRequest(intent)) {
                pendingAndroid6LockRequest = true
            }
        }

        if (handleNfcIntent(intent)) {
            return
        }

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
        val isHomeLaunch =
            isAndroid6HomeIntent(intent)
                || (
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.M
                        && intent.getBooleanExtra(Constants.INTENT_HOME_LAUNCH, false)
                    )
        if (
            System.currentTimeMillis() - lastOnStartTime > 100L
            && isHomeLaunch
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

    private fun isAndroid6HomeIntent(intent: Intent?): Boolean {
        return intent?.action == Intent.ACTION_MAIN
            && intent.hasCategory(Intent.CATEGORY_HOME)
    }

    private fun consumeAndroid6LockRequest(intent: Intent?): Boolean {
        if (
            javaClass != Android6KioskActivity::class.java
            || intent?.getBooleanExtra(Constants.INTENT_ANDROID6_LOCK_TASK, false) != true
        ) {
            return false
        }
        // A later recreation or HOME return must not replay a manual lock request.
        intent.removeExtra(Constants.INTENT_ANDROID6_LOCK_TASK)
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleKeyEvent(this, event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        unregisterReceiver(broadcastReceiver)
        if (!isChangingConfigurations) {
            if (
                userSettings.mqttUseForegroundService
                && MqttManager.isConnected()
            ) {
                MqttManager.disconnect(
                    cause = OutboundDisconnectingEvent.DisconnectCause.SYSTEM_ACTIVITY_DESTROYED
                )
            }
            stopService(
                Intent(this, MqttForegroundService::class.java)
            )
        }
        AuthenticationManager.clear(this)
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

    private fun enableNfcForegroundDispatch() {
        if (!this::userSettings.isInitialized || !userSettings.allowNfc) {
            return
        }

        val adapter = getNfcAdapterOrNull(this) ?: return

        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            flags
        )

        runCatching {
            adapter.enableForegroundDispatch(
                this,
                pendingIntent,
                null,
                null
            )
        }
    }

    private fun disableNfcForegroundDispatch() {
        val adapter = getNfcAdapterOrNull(this) ?: return

        runCatching {
            adapter.disableForegroundDispatch(this)
        }
    }

    private fun handleNfcIntent(intent: Intent): Boolean {
        if (!this::userSettings.isInitialized || !userSettings.allowNfc) {
            return false
        }

        @Suppress("DEPRECATION")
        if (
            intent.action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            return false
        }

        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                NfcAdapter.EXTRA_TAG,
                Tag::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        tag ?: return false

        lifecycleScope.launch(Dispatchers.IO) {
            NfcBridgeManager.onTagScanned(tag)
        }
        return true
    }
}
