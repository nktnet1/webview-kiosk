package uk.nktnet.webviewkiosk.ui.components.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.ui.components.common.LoadingIndicator
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen

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
    val authenticationResult by AuthenticationManager.promptResults.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    val hasValidSession = AuthenticationManager.hasValidSession()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (
                    AuthenticationManager.promptResults.value != AuthenticationManager.AuthenticationResult.Pending
                    && !AuthenticationManager.checkAuthAndRefreshSession()
                ) {
                    showAuthPrompt()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(lifecycleOwner, authenticationResult, hasValidSession) {
        val currentResult = AuthenticationManager.promptResults.value
        if (
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            && !AuthenticationManager.hasValidSession()
            && (currentResult == AuthenticationManager.AuthenticationResult.Loading
                || currentResult == AuthenticationManager.AuthenticationResult.AuthenticationSuccess
                || currentResult == AuthenticationManager.AuthenticationResult.AuthenticationNotSet)
        ) {
            showAuthPrompt()
        }
    }

    when (authenticationResult) {
        is AuthenticationManager.AuthenticationResult.Loading,
        is AuthenticationManager.AuthenticationResult.Pending -> {
            LoadingIndicator("Waiting for authentication...")
        }
        is AuthenticationManager.AuthenticationResult.AuthenticationSuccess,
        is AuthenticationManager.AuthenticationResult.AuthenticationNotSet -> {
            if (hasValidSession) {
                onAuthenticated()
            } else {
                LoadingIndicator("Waiting for authentication...")
            }
        }
        else -> onFailed(authenticationResult)
    }
}
