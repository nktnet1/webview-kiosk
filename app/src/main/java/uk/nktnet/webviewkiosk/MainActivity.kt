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
import uk.nktnet.webviewkiosk.auth.AuthenticationManager
import uk.nktnet.webviewkiosk.config.*
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.handlers.backbutton.BackButtonService
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
import uk.nktnet.webviewkiosk.utils.handlePreviewKeyUnlockEvent
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.tryUnlockTask
import uk.nktnet.webviewkiosk.utils.updateDeviceSettings

class MainActivity : AppCompatActivity() {
    private val navControllerState = mutableStateOf<NavHostController?>(null)
    private var uploadingFileUri: Uri? = null
    private var uploadProgress by mutableFloatStateOf(0f)
    private lateinit var userSettings: UserSettings
    private lateinit var systemSettings: SystemSettings
    private lateinit var backButtonService: BackButtonService

    val restrictionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED) {
                val currentRoute = navControllerState.value?.currentBackStackEntry?.destination?.route
                if (currentRoute != Screen.AdminRestrictionsChanged.route) {
                    navControllerState.value?.navigate(Screen.AdminRestrictionsChanged.route)
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
        updateDeviceSettings(this)

        systemSettings = SystemSettings(this)
        val webContentDir = getWebContentFilesDir(this)

        var toastRef: Toast? = null
        val showToast: (String) -> Unit = { msg ->
            toastRef?.cancel()
            toastRef = Toast.makeText(
                this, msg, Toast.LENGTH_SHORT
            ).apply { show() }
        }

        AuthenticationManager.init(this)

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
        if (
            intent.action == Intent.ACTION_MAIN
            && intent.hasCategory(Intent.CATEGORY_HOME)
            && userSettings.allowGoHome
        ) {
            navControllerState.value?.let {
                systemSettings.intentUrl = userSettings.homeUrl
                navigateToWebViewScreen(it)
            }
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
