package uk.nktnet.webviewkiosk

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
import uk.nktnet.webviewkiosk.auth.AuthenticationManager
import uk.nktnet.webviewkiosk.config.*
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.handlers.backbutton.BackButtonService
import uk.nktnet.webviewkiosk.main.DeviceOwnerManager
import uk.nktnet.webviewkiosk.main.DeviceOwnerMode
import uk.nktnet.webviewkiosk.main.SetupNavHost
import uk.nktnet.webviewkiosk.main.handleMainIntent
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.states.ThemeStateSingleton
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton
import uk.nktnet.webviewkiosk.ui.components.auth.CustomAuthPasswordDialog
import uk.nktnet.webviewkiosk.ui.components.webview.KeepScreenOnOption
import uk.nktnet.webviewkiosk.ui.placeholders.UploadFileProgress
import uk.nktnet.webviewkiosk.ui.theme.WebviewKioskTheme
import uk.nktnet.webviewkiosk.utils.getLocalUrl
import uk.nktnet.webviewkiosk.utils.getWebContentFilesDir
import uk.nktnet.webviewkiosk.utils.handleCustomUnlockShortcut
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
    private lateinit var backButtonService: BackButtonService

    val restrictionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED) {
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                if (currentRoute != Screen.AdminRestrictionsChanged.route) {
                    navController.navigate(Screen.AdminRestrictionsChanged.route)
                }
                updateDeviceSettings(context)
                AuthenticationManager.resetAuthentication()
                AuthenticationManager.hideCustomAuthPrompt()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LockStateSingleton.startMonitoring(application)

        DeviceOwnerManager.init(this)

        if (DeviceOwnerManager.status.value.mode == DeviceOwnerMode.DeviceOwner) {
            setupLockTaskPackage(this)
        } else if (DeviceOwnerManager.status.value.mode == DeviceOwnerMode.Dhizuku) {
            DeviceOwnerManager.requestDhizukuPermission(
                onGranted = {
                    setupLockTaskPackage(this)
                }
            )
        }

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
        updateDeviceSettings(this)

        systemSettings = SystemSettings(this)
        val webContentDir = getWebContentFilesDir(this)

        AuthenticationManager.init(this)

        systemSettings.isFreshLaunch = true

        var toastRef: Toast? = null
        val showToast: (String) -> Unit = { msg ->
            toastRef?.cancel()
            toastRef = Toast.makeText(
                this, msg, Toast.LENGTH_SHORT
            ).apply { show() }
        }

        if (userSettings.lockOnLaunch) {
            tryLockTask(this, showToast)
        }

        if (intent != null) {
            saveIntentUrl(intent)
        }

        setContent {
            navController = rememberNavController()

            KeepScreenOnOption()

            val waitingForUnlock by WaitingForUnlockStateSingleton.waitingForUnlock.collectAsState()
            val biometricResult by AuthenticationManager.promptResults.collectAsState()
            val activity = LocalActivity.current

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
                        tryUnlockTask(activity, showToast)
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
        AuthenticationManager.init(this)
    }

    override fun onResume() {
        super.onResume()
        updateDeviceSettings(this)
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
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!this::navController.isInitialized) {
            return
        }
        if (
            intent.action == Intent.ACTION_MAIN
            && intent.hasCategory(Intent.CATEGORY_HOME)
            && userSettings.allowGoHome
        ) {
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
        if (handleCustomUnlockShortcut(this, event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        unregisterReceiver(restrictionsReceiver)
        super.onDestroy()
    }

    @Deprecated("For Android 5.0 (SDK 21-22)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            AuthenticationManager.handleLollipopDeviceCredentialResult(requestCode, resultCode)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) =
        backButtonService.onKeyDown(keyCode) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent) =
        backButtonService.onKeyUp(keyCode) || super.onKeyUp(keyCode, event)

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent) =
        backButtonService.onKeyLongPress(keyCode) || super.onKeyLongPress(keyCode, event)
}
