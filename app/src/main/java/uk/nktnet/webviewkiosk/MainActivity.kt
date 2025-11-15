package com.nktnet.webview_kiosk

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
import com.nktnet.webview_kiosk.auth.AuthenticationManager
import com.nktnet.webview_kiosk.config.*
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.handlers.backbutton.BackButtonService
import com.nktnet.webview_kiosk.main.SetupNavHost
import com.nktnet.webview_kiosk.main.handleMainIntent
import com.nktnet.webview_kiosk.states.InactivityStateSingleton
import com.nktnet.webview_kiosk.states.LockStateSingleton
import com.nktnet.webview_kiosk.states.ThemeStateSingleton
import com.nktnet.webview_kiosk.states.WaitingForUnlockStateSingleton
import com.nktnet.webview_kiosk.ui.components.auth.CustomAuthPasswordDialog
import com.nktnet.webview_kiosk.ui.components.webview.KeepScreenOnOption
import com.nktnet.webview_kiosk.ui.placeholders.UploadFileProgress
import com.nktnet.webview_kiosk.ui.theme.WebviewKioskTheme
import com.nktnet.webview_kiosk.utils.getLocalUrl
import com.nktnet.webview_kiosk.utils.getWebContentFilesDir
import com.nktnet.webview_kiosk.utils.handlePreviewKeyUnlockEvent
import com.nktnet.webview_kiosk.utils.setupLockTaskPackage
import com.nktnet.webview_kiosk.utils.tryLockTask
import com.nktnet.webview_kiosk.utils.tryUnlockTask
import com.nktnet.webview_kiosk.utils.updateDeviceSettings

class MainActivity : AppCompatActivity() {
    private val navControllerState = mutableStateOf<NavHostController?>(null)
    private var uploadingFileUri: Uri? = null
    private var uploadProgress by mutableFloatStateOf(0f)
    private lateinit var userSettings: UserSettings
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

        val systemSettings = SystemSettings(this)
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
        InactivityStateSingleton.onUserInteraction()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            AuthenticationManager.resetAuthentication()
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
