package com.nktnet.webview_kiosk.ui.components.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.managers.AuthenticationManager
import com.nktnet.webview_kiosk.ui.components.common.LoadingIndicator
import com.nktnet.webview_kiosk.utils.navigateToWebViewScreen

private fun showAuthPrompt() {
    AuthenticationManager.showAuthenticationPrompt(
        title = "Authentication Required",
        description = "Please authenticate to access settings"
    )
}

@Composable
fun RequireAuthWrapper(
    navController: NavController,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        RequireAuthentication(
            onAuthenticated = content,
            onFailed = { errorResult ->
                AuthenticationErrorDisplay(
                    errorResult = errorResult,
                    onRetry = {
                        showAuthPrompt()
                    },
                    onCancel = {
                        navigateToWebViewScreen(navController)
                    },
                )
            }
        )
    }
}

@Composable
private fun RequireAuthentication(
    onAuthenticated: @Composable () -> Unit,
    onFailed: @Composable (AuthenticationManager.AuthenticationResult?) -> Unit
) {
    val authenticationResult by AuthenticationManager.promptResults.collectAsState(
        initial = AuthenticationManager.AuthenticationResult.Loading
    )

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (!AuthenticationManager.checkAuthAndRefreshSession()) {
                    showAuthPrompt()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when (authenticationResult) {
        is AuthenticationManager.AuthenticationResult.Loading -> {
            LoadingIndicator("Waiting for authentication...")
        }
        is AuthenticationManager.AuthenticationResult.AuthenticationSuccess,
        is AuthenticationManager.AuthenticationResult.AuthenticationNotSet -> {
            onAuthenticated()
        }
        else -> onFailed(authenticationResult)
    }
}
