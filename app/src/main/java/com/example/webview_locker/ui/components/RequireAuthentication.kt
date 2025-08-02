package com.example.webview_locker.ui.components

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.runtime.*
import com.example.webview_locker.auth.BiometricPromptManager

@Composable
fun RequireAuthentication(
    promptManager: BiometricPromptManager,
    onAuthenticated: @Composable () -> Unit,
    onFailed: @Composable (BiometricPromptManager.BiometricResult?) -> Unit
) {
    val biometricResult by promptManager.promptResults.collectAsState(initial = null)
    val enrollLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { /* no-op or log */ }
    )

    LaunchedEffect(biometricResult) {
        if (biometricResult is BiometricPromptManager.BiometricResult.AuthenticationNotSet && Build.VERSION.SDK_INT >= 30) {
            enrollLauncher.launch(
                Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                    )
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        promptManager.showBiometricPrompt(
            title = "Authentication Required",
            description = "Please authenticate to proceed"
        )
    }

    when (biometricResult) {
        is BiometricPromptManager.BiometricResult.AuthenticationSuccess -> onAuthenticated()
        else -> onFailed(biometricResult)
    }
}
