package com.nktnet.webview_kiosk.ui.components

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.auth.BiometricPromptManager


@Composable
fun RequireAuthWrapper(
    promptManager: BiometricPromptManager,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        RequireAuthentication(
            promptManager = promptManager,
            onAuthenticated = { content() },
            onFailed = { errorResult ->
                AuthenticationErrorDisplay(errorResult = errorResult) {
                    promptManager.showBiometricPrompt(
                        title = "Authentication Required",
                        description = "Please authenticate to access settings"
                    )
                }
            }
        )
    }
}
@Composable
private fun RequireAuthentication(
    promptManager: BiometricPromptManager,
    onAuthenticated: @Composable () -> Unit,
    onFailed: @Composable (BiometricPromptManager.BiometricResult?) -> Unit
) {
    val biometricResult by promptManager.promptResults.collectAsState(initial = null)
    val enrollLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { /* no-op */ }
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
        if (!promptManager.isAuthenticated) {
            promptManager.showBiometricPrompt(
                title = "Authentication Required",
                description = "Please authenticate to proceed"
            )
        }
    }

    when {
        promptManager.isAuthenticated -> onAuthenticated()
        biometricResult is BiometricPromptManager.BiometricResult.AuthenticationSuccess -> onAuthenticated()
        biometricResult == null -> LoadingIndicator("Waiting for authentication...")
        else -> onFailed(biometricResult)
    }
}
