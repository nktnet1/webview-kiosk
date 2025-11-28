package com.nktnet.webview_kiosk.ui.components.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nktnet.webview_kiosk.auth.AuthenticationManager
import com.nktnet.webview_kiosk.ui.components.common.LoadingIndicator

private fun showAuthPrompt() {
    AuthenticationManager.showAuthenticationPrompt(
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
    onFailed: @Composable (AuthenticationManager.AuthenticationResult?) -> Unit
) {
    val authenticationResult by AuthenticationManager.promptResults.collectAsState(initial = AuthenticationManager.AuthenticationResult.Loading)

    LaunchedEffect(Unit) {
        if (!AuthenticationManager.checkAuthAndRefreshSession()) {
            showAuthPrompt()
        }
    }

    when (authenticationResult) {
        is AuthenticationManager.AuthenticationResult.Loading -> LoadingIndicator("Waiting for authentication...")
        is AuthenticationManager.AuthenticationResult.AuthenticationSuccess,
        is AuthenticationManager.AuthenticationResult.AuthenticationNotSet -> onAuthenticated()
        else -> onFailed(authenticationResult)
    }
}
