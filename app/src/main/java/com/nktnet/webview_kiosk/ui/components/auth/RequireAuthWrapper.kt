package com.nktnet.webview_kiosk.ui.components.auth

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.auth.BiometricPromptManager
import com.nktnet.webview_kiosk.ui.components.common.LoadingIndicator


@Composable
fun RequireAuthWrapper(
    promptManager: BiometricPromptManager,
    content: @Composable () -> Unit
) {
    val showPrompt = remember {
        {
            promptManager.showBiometricPrompt(
                title = "Authentication Required",
                description = "Please authenticate to access settings"
            )
        }
    }

    Box(modifier = Modifier.padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        RequireAuthentication(
            promptManager = promptManager,
            onAuthenticated = { content() },
            onFailed = { errorResult ->
                AuthenticationErrorDisplay(errorResult = errorResult, onRetry = showPrompt)
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

    LaunchedEffect(Unit) {
        if (!promptManager.checkAuthAndRefreshSession()) {
            promptManager.showBiometricPrompt(
                title = "Authentication Required",
                description = "Please authenticate to proceed"
            )
        }
    }

    when {
        promptManager.checkAuthAndRefreshSession()
                || biometricResult is BiometricPromptManager.BiometricResult.AuthenticationSuccess
                || biometricResult is BiometricPromptManager.BiometricResult.AuthenticationNotSet
            -> onAuthenticated()
        biometricResult == null -> LoadingIndicator("Waiting for authentication...")
        else -> onFailed(biometricResult)
    }
}
