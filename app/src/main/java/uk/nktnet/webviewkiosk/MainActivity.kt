package uk.nktnet.webviewkiosk

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
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
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
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.ClientCertificateInstaller
import uk.nktnet.webviewkiosk.ui.components.webview.KeepScreenOnOption
import uk.nktnet.webviewkiosk.ui.placeholders.UploadFileProgress
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes
import uk.nktnet.webviewkiosk.ui.screens.SetupNavHost
import uk.nktnet.webviewkiosk.ui.theme.WebviewKioskTheme
import uk.nktnet.webviewkiosk.utils.ImmersiveModeRecovery
import uk.nktnet.webviewkiosk.utils.enterImmersiveMode
import uk.nktnet.webviewkiosk.utils.getBooleanExtraSafely
import uk.nktnet.webviewkiosk.utils.getLocalFileLink
import uk.nktnet.webviewkiosk.utils.getParcelableExtraSafely
import uk.nktnet.webviewkiosk.utils.getWebContentFilesDir
import uk.nktnet.webviewkiosk.utils.handleKeyEvent
import uk.nktnet.webviewkiosk.utils.handleMainIntent
import uk.nktnet.webviewkiosk.utils.launchAndroid6KioskTask
import uk.nktnet.webviewkiosk.utils.navigateToAdminRestrictionsChangedIfReady
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage
import uk.nktnet.webviewkiosk.utils.shouldBeImmersed
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.tryUnlockTask
import uk.nktnet.webviewkiosk.utils.updateDeviceSettings
import uk.nktnet.webviewkiosk.utils.webview.NfcBridgeManager
import uk.nktnet.webviewkiosk.utils.webview.NfcForegroundDispatch
import uk.nktnet.webviewkiosk.utils.webview.WebViewStartup

open class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavHostController
    private var uploadingFileUri by mutableStateOf<Uri?>(null)
    private var uploadProgress by mutableFloatStateOf(0f)
    private var pendingAdminRestrictionsNavigation by mutableStateOf(false)
    private lateinit var userSettings: UserSettings
    private lateinit var systemSettings: SystemSettings
    private lateinit var backButtonService: BackButtonManager
    private lateinit var clientCertificateInstaller: ClientCertificateInstaller
    private var lastOnStartTime = 0L
    private var pendingAndroid6HomeRedirect = false
    private var pendingAndroid6LockRequest = false
    private var pendingLockOnLaunch = false
    private var resumedForLockTask = false
    private var hasFocusedWindow = false
    private var activityCreatedAtMillis = 0L
    private var pendingDhizukuPermissionRequest = false
    private var deviceOwnerInitJob: Job? = null
    private var unregisterMqttCommandHost: (() -> Unit)? = null
    private val nfcForegroundDispatch by lazy {
        NfcForegroundDispatch(this, isAllowed = { userSettings.allowNfc })
    }

    // HOME and normal LAUNCHER tasks can coexist on any Android version (API 23
    // may also keep the private kiosk task alive). Only one activity should own
    // process-wide MQTT events, authentication and the active WebView UI.
    companion object {
        private val createdHosts = mutableSetOf<MainActivity>()
        private val startedHosts = mutableSetOf<MainActivity>()
        private var activeEventHost by mutableStateOf<MainActivity?>(null)
    }

    internal fun isApplicationEventHost(): Boolean = activeEventHost === this

    val broadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!isApplicationEventHost()) {
                    return
                }
                when (intent.action) {
                    Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED -> {
                        // The controller can exist before the NavHost installs its
                        // graph (or while the NavHost is temporarily absent).
                        // Deliver the notice after a destination becomes active.
                        pendingAdminRestrictionsNavigation = true
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
                    Intent.ACTION_SCREEN_ON -> {
                        nfcForegroundDispatch.onScreenOn()
                        if (userSettings.allowNfc) {
                            NfcBridgeManager.rearmActiveReaders()
                        }
                    }
                    NfcAdapter.ACTION_ADAPTER_STATE_CHANGED -> {
                        val state =
                            intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1)
                        nfcForegroundDispatch.onAdapterStateChanged(state)
                        if (state == NfcAdapter.STATE_ON && userSettings.allowNfc) {
                            NfcBridgeManager.rearmActiveReaders()
                        }
                    }
                    else -> Unit
                }
            }
        }

    private var immersiveModeRecovery: ImmersiveModeRecovery? = null

    private fun canRecoverImmersiveMode(): Boolean =
        this::navController.isInitialized &&
            this::userSettings.isInitialized &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            !isFinishing &&
            !isDestroyed &&
            isApplicationEventHost() &&
            navController.currentDestination?.route == Screen.WebView.route &&
            shouldBeImmersed(this, userSettings)

    override fun onCreate(savedInstanceState: Bundle?) {
        activityCreatedAtMillis = SystemClock.uptimeMillis()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Register both KeyChain-related activity results before STARTED, even when
        // the
        // mutual-TLS settings screen is not composed (e.g. while the picker is
        // visible).
        clientCertificateInstaller =
            ClientCertificateInstaller(
                context = applicationContext,
                lifecycleOwner = this,
                activityResultRegistry = activityResultRegistry,
                activityScope = lifecycleScope,
            )
        WebViewStartup.start(applicationContext)
        createdHosts.add(this)
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            // On API 23, enableEdgeToEdge() uses legacy systemUiVisibility layout
            // flags.
            // AppCompat can overwrite those flags while creating the decor view,
            // leaving the
            // content already inset by the framework before Compose applies its own
            // insets.
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        CustomNotificationManager.init(applicationContext)
        userSettings = UserSettings(this)
        systemSettings = SystemSettings(this)
        immersiveModeRecovery =
            ImmersiveModeRecovery(
                windowView = window.decorView,
                canRecover = ::canRecoverImmersiveMode,
                recover = { enterImmersiveMode(this) },
            )
        DeviceOwnerManager.init(this)
        pendingDhizukuPermissionRequest =
            userSettings.dhizukuRequestPermissionOnLaunch
        // https://github.com/nktnet1/webview-kiosk/pull/195
        getExternalFilesDir(null)

        if (DeviceOwnerManager.hasOwnerPermission(this)) {
            setupLockTaskPackage(this)
        }

        LockStateSingleton.startMonitoring(application)

        backButtonService = BackButtonManager(lifecycleScope = lifecycleScope)
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
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            },
        )

        if (!MqttManager.isInitialized()) {
            MqttManager.updateConfig(applicationContext)
        }
        startMqttMessageProcessing()

        val webContentDir = getWebContentFilesDir(this)

        AuthenticationManager.init(this)

        systemSettings.isFreshLaunch = true

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            // API 23 must enter lock task only after the activity is resumed. Keep
            // these as
            // one-shot launch actions so returning HOME after an unlock does not
            // lock again.
            val lockRequested = consumeAndroid6LockRequest(intent)
            pendingAndroid6HomeRedirect =
                javaClass == HomeActivity::class.java && isAndroid6HomeIntent(intent)
            pendingAndroid6LockRequest =
                !pendingAndroid6HomeRedirect &&
                    (userSettings.lockOnLaunch || lockRequested)
        } else {
            // startLockTask() requires a foreground activity. Wait for a focused
            // window as well so locking cannot interfere with initial focus.
            pendingLockOnLaunch = userSettings.lockOnLaunch
        }

        if (intent != null) {
            saveIntentUrl(intent)
        }

        setContent {
            navController = rememberNavController()
            val webViewReady by WebViewStartup.ready.collectAsState()
            val blocker = remember { ContentBlockingService.get(applicationContext) }
            val ruleLoadStage by blocker.loadStage.collectAsState()
            val rulesReady by blocker.initialLoadComplete.collectAsState()
            val startupMessage =
                if (webViewReady) {
                    null
                } else if (rulesReady) {
                    stringResource(R.string.ui_starting_webview)
                } else {
                    ruleLoadStage?.let { stringResource(it.displayLabelRes) }
                        ?: stringResource(R.string.ui_preparing_content_blocking)
                }
            KeepScreenOnOption()

            val waitingForUnlock by
                WaitingForUnlockStateSingleton.waitingForUnlock.collectAsState()
            val biometricResult by
                AuthenticationManager.promptResults.collectAsState()
            val context = LocalContext.current

            val activity = LocalActivity.current
            val handlesApplicationEvents = isApplicationEventHost()
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            LaunchedEffect(
                pendingAdminRestrictionsNavigation,
                currentBackStackEntry,
                webViewReady,
                uploadingFileUri,
                handlesApplicationEvents,
            ) {
                if (
                    pendingAdminRestrictionsNavigation &&
                        handlesApplicationEvents &&
                        webViewReady &&
                        uploadingFileUri == null &&
                        currentBackStackEntry != null &&
                        navigateToAdminRestrictionsChangedIfReady(navController)
                ) {
                    pendingAdminRestrictionsNavigation = false
                }
            }
            // Only the active host may display or consume the process-wide
            // authentication result. API 23 additionally requires RESUMED because
            // its routing and private kiosk tasks may briefly overlap.
            val lifecycleState by lifecycle.currentStateFlow.collectAsState()
            val handlesAuthentication =
                handlesApplicationEvents &&
                    (Build.VERSION.SDK_INT != Build.VERSION_CODES.M ||
                        lifecycleState == Lifecycle.State.RESUMED)

            LaunchedEffect(Unit) {
                RemoteMessageManager.settingsAppliedFlow.collect { settings ->
                    if (
                        isApplicationEventHost() && settings.message.reloadActivity
                    ) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            delay(100.milliseconds)
                            if (
                                settings.message.reloadActivity &&
                                    isApplicationEventHost()
                            ) {
                                updateDeviceSettings(context)
                            }
                        }

                        // Counterintuitive, but this acts as a "Refresh" of the
                        // webview screen,
                        // which will recreate + apply settings.
                        // If we're on another screen though (e.g. settings), then
                        // let the user
                        // decide when to navigate back.
                        if (
                            navController.currentDestination?.route ==
                                Screen.WebView.route
                        ) {
                            navigateToWebViewScreen(navController)
                        }
                    }
                }
            }

            LaunchedEffect(
                waitingForUnlock,
                biometricResult,
                handlesAuthentication,
            ) {
                val isAndroid6 = Build.VERSION.SDK_INT == Build.VERSION_CODES.M
                if (
                    !handlesAuthentication ||
                        (isAndroid6 &&
                            (lifecycle.currentState != Lifecycle.State.RESUMED ||
                                !WaitingForUnlockStateSingleton.waitingForUnlock
                                    .value))
                ) {
                    return@LaunchedEffect
                }
                if (waitingForUnlock) {
                    if (
                        biometricResult ==
                            AuthenticationManager.AuthenticationResult.Loading ||
                            biometricResult ==
                                AuthenticationManager.AuthenticationResult.Pending
                    ) {
                        return@LaunchedEffect
                    }
                    if (isAndroid6) {
                        // Consume the result before emitting a suspending event. A
                        // second host
                        // must not process the same unlock if activity lifecycles
                        // change.
                        WaitingForUnlockStateSingleton.stopWaiting()
                    }
                    if (
                        biometricResult ==
                            AuthenticationManager.AuthenticationResult
                                .AuthenticationSuccess ||
                            biometricResult ==
                                AuthenticationManager.AuthenticationResult
                                    .AuthenticationNotSet
                    ) {
                        val unlocked = tryUnlockTask(activity)
                        if (isAndroid6) {
                            if (unlocked) {
                                // stopWaiting changes this effect's key. Send the
                                // notification
                                // in the activity scope so that recomposition cannot
                                // cancel it.
                                lifecycleScope.launch {
                                    WaitingForUnlockStateSingleton
                                        .emitUnlockSuccess()
                                }
                            }
                        } else if (unlocked) {
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
            val insetsController =
                remember(window) {
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
                        addFlags(
                            WindowManager.LayoutParams
                                .FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                        )

                        // API 21-22 cannot render dark status-bar icons, so a white
                        // status bar
                        // would make the fixed light icons unreadable in light
                        // theme.
                        @Suppress("DEPRECATION")
                        statusBarColor =
                            if (
                                !isDarkTheme &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ) {
                                Color.WHITE
                            } else if (isDarkTheme) {
                                Color.BLACK
                            } else {
                                Color.argb(0x80, 0x1B, 0x1B, 0x1B)
                            }

                        // Dark navigation-bar icons were added in API 26. Keep a
                        // dark scrim on
                        // API 21-25 in light theme so the fixed light navigation
                        // buttons remain
                        // visible, while API 26+ retains the original light/dark bar
                        // colours.
                        @Suppress("DEPRECATION")
                        navigationBarColor =
                            if (isDarkTheme) {
                                Color.BLACK
                            } else if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ) {
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
                    modifier = Modifier.fillMaxSize(),
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
                            },
                            onFailed = {
                                uploadingFileUri = null
                                uploadProgress = 0f
                            },
                        )
                    }
                        ?: run {
                            if (handlesAuthentication) {
                                CustomAuthPasswordDialog()
                            }
                            if (handlesApplicationEvents) {
                                if (webViewReady) {
                                    // Avoid racing the first WebView constructor
                                    // against native startup.
                                    // Keep the controller alive while the provider
                                    // initializes.
                                    SetupNavHost(navController)
                                } else {
                                    Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment =
                                                Alignment.CenterHorizontally,
                                            verticalArrangement =
                                                Arrangement.spacedBy(16.dp),
                                        ) {
                                            CircularProgressIndicator()
                                            Text(
                                                startupMessage.orEmpty(),
                                                style =
                                                    MaterialTheme.typography
                                                        .bodyMedium,
                                                fontStyle = FontStyle.Italic,
                                                color =
                                                    MaterialTheme.colorScheme
                                                        .onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
        logWindowLifecycle("onCreate completed")
    }

    internal fun openClientCertificatePicker() =
        clientCertificateInstaller.openPicker()

    private fun canHandleMqttMessages(): Boolean =
        isApplicationEventHost() &&
            this in startedHosts &&
            userSettings.mqttEnabled &&
            !MqttForegroundService.isHandlingMessages()

    private fun startMqttMessageProcessing() {
        unregisterMqttCommandHost =
            RemoteMessageManager.registerMqttCommandHost(applicationContext) {
                canHandleMqttMessages()
            }
        // Subscribe before onStart connects MQTT; composition may not exist for its
        // first delivery.
        // Keep one pair for this activity and recheck eligibility before claiming
        // queued messages.
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.requestsFlow.collect { request ->
                if (
                    canHandleMqttMessages() &&
                        request.source ==
                            RemoteMessageManager.RemoteMessage.Source.MQTT &&
                        request.tryClaim()
                ) {
                    RemoteInboundHandler.handleInboundMqttRequest(
                        this@MainActivity,
                        request.message,
                    )
                }
            }
        }
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.settingsFlow.collect { settings ->
                if (
                    canHandleMqttMessages() &&
                        settings.source ==
                            RemoteMessageManager.RemoteMessage.Source.MQTT &&
                        settings.tryClaim()
                ) {
                    RemoteInboundHandler.handleInboundSettings(
                        this@MainActivity,
                        settings.message,
                        settings.source,
                    )
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
        val isFirstStartedHost = startedHosts.isEmpty()
        startedHosts.add(this)
        activeEventHost = this
        super.onStart()
        lastOnStartTime = System.currentTimeMillis()
        AuthenticationManager.init(this)
        DeviceOwnerManager.init(this)
        updateDeviceSettings(this)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                DeviceOwnerManager.status.value.mode !in
                    setOf(DeviceOwnerMode.DeviceOwner, DeviceOwnerMode.ProfileOwner)
        ) {
            deviceOwnerInitJob?.cancel()
            deviceOwnerInitJob = lifecycleScope.launch {
                val mode = DeviceOwnerManager.initWithDhizukuRetry(this@MainActivity)
                if (mode == DeviceOwnerMode.Dhizuku) {
                    setupLockTaskPackage(this@MainActivity)
                    updateDeviceSettings(this@MainActivity)
                    if (
                        pendingDhizukuPermissionRequest && isApplicationEventHost()
                    ) {
                        pendingDhizukuPermissionRequest = false
                        DeviceOwnerManager.requestDhizukuPermission(
                            context = this@MainActivity,
                            onGranted = {
                                setupLockTaskPackage(this@MainActivity)
                            },
                        )
                    }
                }
            }
        }
        if (userSettings.mqttEnabled) {
            MqttManager.connect(applicationContext)
            if (
                isFirstStartedHost &&
                    userSettings.mqttUseForegroundService &&
                    MqttManager.isConnected()
            ) {
                MqttManager.publishAppForegroundEvent()
            }
        }
        logWindowLifecycle("onStart completed")
    }

    override fun onResume() {
        super.onResume()
        resumedForLockTask = true
        if (
            this in startedHosts &&
                !pendingAndroid6HomeRedirect &&
                !isApplicationEventHost()
        ) {
            // A started task can resume without another onStart (e.g. when
            // switching between visible tasks). Keep UI and auth ownership on
            // the foreground activity, not the last activity that started.
            activeEventHost = this
            AuthenticationManager.init(this)
        }
        backButtonService.onBackPressedCallback.isEnabled = true
        nfcForegroundDispatch.onResume()
        if (userSettings.allowNfc) {
            NfcBridgeManager.rearmActiveReaders()
        }

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
        } else {
            startPendingLaunchLockIfFocused()
        }
        immersiveModeRecovery?.resume()
        logWindowLifecycle("onResume completed")
    }

    private fun startPendingLaunchLockIfFocused() {
        if (
            !pendingLockOnLaunch ||
                !resumedForLockTask ||
                !hasFocusedWindow ||
                !isApplicationEventHost() ||
                isFinishing ||
                isDestroyed
        ) {
            return
        }
        // Consume once: focus may toggle repeatedly and unlocking must not
        // immediately relock this task.
        pendingLockOnLaunch = false
        tryLockTask(this)
    }

    private fun logWindowLifecycle(event: String) {
        Log.i(
            "MainActivityWindow",
            "${javaClass.simpleName} task=$taskId $event " +
                "elapsed=${SystemClock.uptimeMillis() - activityCreatedAtMillis}ms " +
                "eventHost=${isApplicationEventHost()}",
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hasFocusedWindow = hasFocus
        logWindowLifecycle("windowFocus=$hasFocus")
        if (hasFocus) {
            startPendingLaunchLockIfFocused()
        }
        // Focus can return without onResume (e.g. after the notification shade).
        immersiveModeRecovery?.onWindowFocusChanged(hasFocus)
        if (hasFocus && isApplicationEventHost()) {
            nfcForegroundDispatch.onWindowFocusGained()
            if (userSettings.allowNfc) {
                NfcBridgeManager.rearmActiveReaders()
            }
        }
    }

    override fun onPause() {
        resumedForLockTask = false
        hasFocusedWindow = false
        immersiveModeRecovery?.pause()
        nfcForegroundDispatch.onPause()
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        UserInteractionStateSingleton.onUserInteraction()
        immersiveModeRecovery?.onUserInteraction()
    }

    override fun onStop() {
        super.onStop()
        startedHosts.remove(this)
        if (activeEventHost === this && startedHosts.isNotEmpty()) {
            // When another task is still started, transfer ownership rather than
            // stranding process-wide events or authentication on the stopped host.
            val nextHost = startedHosts.last()
            activeEventHost = nextHost
            AuthenticationManager.init(nextHost)
        }
        if (!isChangingConfigurations && startedHosts.isEmpty()) {
            AuthenticationManager.resetAuthentication(
                preserveExternalActivitySession = true
            )
            if (MqttForegroundService.isHandlingMessages()) {
                if (MqttManager.isConnected()) {
                    MqttManager.publishAppBackgroundEvent()
                }
            } else {
                MqttManager.disconnect(
                    cause =
                        OutboundDisconnectingEvent.DisconnectCause
                            .SYSTEM_ACTIVITY_STOPPED
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            setIntent(intent)
            if (
                javaClass == HomeActivity::class.java && isAndroid6HomeIntent(intent)
            ) {
                // Always route HOME back to the private task. Reusing that task must
                // not
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
            intent.getBooleanExtraSafely(
                Constants.INTENT_NAVIGATE_TO_WEBVIEW_SCREEN,
                false,
            )
        ) {
            if (callingPackage == packageName) {
                navigateToWebViewScreen(navController)
            }
            return
        }
        val isHomeLaunch =
            isAndroid6HomeIntent(intent) ||
                (Build.VERSION.SDK_INT == Build.VERSION_CODES.M &&
                    intent.getBooleanExtraSafely(
                        Constants.INTENT_HOME_LAUNCH,
                        false,
                    ))
        if (
            System.currentTimeMillis() - lastOnStartTime > 100L &&
                isHomeLaunch &&
                userSettings.allowGoHome
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
        return intent?.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)
    }

    private fun consumeAndroid6LockRequest(intent: Intent?): Boolean {
        if (
            javaClass != Android6KioskActivity::class.java ||
                intent?.getBooleanExtraSafely(
                    Constants.INTENT_ANDROID6_LOCK_TASK,
                    false,
                ) != true
        ) {
            return false
        }
        // A later recreation or HOME return must not replay a manual lock request.
        intent.removeExtra(Constants.INTENT_ANDROID6_LOCK_TASK)
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return handleKeyEvent(this, event) || super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        immersiveModeRecovery?.pause()
        immersiveModeRecovery = null
        createdHosts.remove(this)
        startedHosts.remove(this)
        unregisterMqttCommandHost?.invoke()
        unregisterMqttCommandHost = null
        if (activeEventHost === this) {
            val nextStartedHost = startedHosts.lastOrNull()
            activeEventHost = nextStartedHost ?: createdHosts.lastOrNull()
            if (nextStartedHost != null) {
                AuthenticationManager.init(nextStartedHost)
            }
        }
        unregisterReceiver(broadcastReceiver)
        if (!isChangingConfigurations && createdHosts.isEmpty()) {
            if (!MqttForegroundService.isHandlingMessages()) {
                MqttManager.disconnect(
                    cause =
                        OutboundDisconnectingEvent.DisconnectCause
                            .SYSTEM_ACTIVITY_DESTROYED
                )
            }
            // Keep an opted-in start request queued; an accepted service can restore
            // MQTT.
            if (
                !(userSettings.mqttEnabled && userSettings.mqttUseForegroundService)
            ) {
                stopService(Intent(this, MqttForegroundService::class.java))
            }
        }
        AuthenticationManager.clear(this)
        super.onDestroy()
    }

    @Deprecated("For Android 5.0 (SDK 21-22)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            AuthenticationManager.handleLollipopDeviceCredentialResult(
                requestCode,
                resultCode,
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKeyEvent(this, event)) {
            return true
        }
        if (backButtonService.onKeyDown(keyCode)) {
            // The Huawei fallback consumes Back-down; retain framework tracking for
            // short taps.
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return (handleKeyEvent(this, event) ||
            backButtonService.onKeyUp(keyCode) ||
            super.onKeyUp(keyCode, event))
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        return (backButtonService.onKeyLongPress(keyCode) ||
            super.onKeyLongPress(keyCode, event))
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

        val tag =
            intent.getParcelableExtraSafely(NfcAdapter.EXTRA_TAG, Tag::class.java)

        tag ?: return false

        lifecycleScope.launch(Dispatchers.IO) {
            NfcBridgeManager.onTagScanned(tag)
        }
        return true
    }
}
