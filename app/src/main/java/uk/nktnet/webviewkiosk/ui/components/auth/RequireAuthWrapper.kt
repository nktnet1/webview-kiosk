package uk.nktnet.webviewkiosk.ui.components.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import uk.nktnet.webviewkiosk.auth.BiometricPromptManager
import uk.nktnet.webviewkiosk.ui.components.common.LoadingIndicator

private fun showAuthPrompt() {
    BiometricPromptManager.showBiometricPrompt(
        title = "Authentication Required",
        description = "Please authenticate to access settings"
    )
}

@Composable
fun RequireAuthWrapper(
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        RequireAuthentication(
            onAuthenticated = content,
            onFailed = { errorResult ->
                AuthenticationErrorDisplay(errorResult = errorResult, onRetry = { showAuthPrompt() })
            }
        )
    }
}

@Composable
private fun RequireAuthentication(
    onAuthenticated: @Composable () -> Unit,
    onFailed: @Composable (BiometricPromptManager.BiometricResult?) -> Unit
) {
    val biometricResult by BiometricPromptManager.promptResults.collectAsState(initial = BiometricPromptManager.BiometricResult.Loading)

    LaunchedEffect(Unit) {
        if (!BiometricPromptManager.checkAuthAndRefreshSession()) {
            showAuthPrompt()
        }
    }

    when (biometricResult) {
        is BiometricPromptManager.BiometricResult.Loading -> LoadingIndicator("Waiting for authentication...")
        is BiometricPromptManager.BiometricResult.AuthenticationSuccess,
        is BiometricPromptManager.BiometricResult.AuthenticationNotSet -> onAuthenticated()
        else -> onFailed(biometricResult)
    }
}
