package uk.nktnet.webviewkiosk.ui.components.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.SettingsAuthRequirementOption
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.ui.components.common.LoadingIndicator
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen

private fun showAuthPrompt(context: Context) {
    AuthenticationManager.showAuthenticationPrompt(
        title = context.getString(R.string.ui_authentication_required),
        description =
            context.getString(R.string.ui_please_authenticate_to_access_settings),
    )
}

// Recheck the clock when content changes; composable lambdas can keep the same
// identity while their captured values change, allowing normal skipping.
@Composable
@NonSkippableComposable
fun RequireAuthWrapper(
    navController: NavController,
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userSettings = remember(context) { UserSettings(context) }
    val requiresAuth =
        remember(context) {
            mutableStateOf(
                userSettings.settingsAuthRequirement ==
                    SettingsAuthRequirementOption.REQUIRE
            )
        }
    // Reading SharedPreferences is not observable by Compose. Without a listener,
    // changing Off to Require while Settings remains open can leave the bypass
    // branch mounted until an unrelated recomposition or navigation occurs.
    DisposableEffect(context, userSettings) {
        fun refreshRequirement() {
            val required =
                userSettings.settingsAuthRequirement ==
                    SettingsAuthRequirementOption.REQUIRE
            if (requiresAuth.value && !required) {
                AuthenticationManager.clearSettingsAuthentication()
            }
            requiresAuth.value = required
        }

        val prefs =
            context.getSharedPreferences(
                UserSettingsKeys.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == UserSettingsKeys.Device.SETTINGS_AUTH_REQUIREMENT) {
                refreshRequirement()
            }
        }
        val restrictionsReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    refreshRequirement()
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        ContextCompat.registerReceiver(
            context,
            restrictionsReceiver,
            IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        // Also close the gap between the first preference read and registration.
        refreshRequirement()
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            context.unregisterReceiver(restrictionsReceiver)
        }
    }
    if (!requiresAuth.value) {
        content()
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        RequireAuthentication(
            onAuthenticated = content,
            onFailed = { errorResult ->
                AuthenticationErrorDisplay(
                    errorResult = errorResult,
                    onRetry = {
                        showAuthPrompt(context)
                    },
                    onCancel = {
                        navigateToWebViewScreen(navController)
                    },
                )
            },
        )
    }
}

@Composable
@NonSkippableComposable
private fun RequireAuthentication(
    onAuthenticated: @Composable () -> Unit,
    onFailed: @Composable (AuthenticationManager.AuthenticationResult?) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authenticationResult by AuthenticationManager.promptResults.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    // Session time is not Compose state. A lifecycle transition must also
    // re-evaluate it when authentication renews without changing the result.
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val hasValidSession = AuthenticationManager.hasValidSession()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // A paused activity can resume without another ON_START event.
            val currentResult = AuthenticationManager.promptResults.value
            val checkOnResume =
                event == Lifecycle.Event.ON_RESUME &&
                    (currentResult ==
                        AuthenticationManager.AuthenticationResult.Loading ||
                        currentResult ==
                            AuthenticationManager.AuthenticationResult
                                .AuthenticationSuccess ||
                        currentResult ==
                            AuthenticationManager.AuthenticationResult
                                .AuthenticationNotSet)
            if (event == Lifecycle.Event.ON_START || checkOnResume) {
                if (
                    currentResult !=
                        AuthenticationManager.AuthenticationResult.Pending &&
                        !AuthenticationManager.checkAuthAndRefreshSession()
                ) {
                    showAuthPrompt(context)
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(
        lifecycleOwner,
        lifecycleState,
        authenticationResult,
        hasValidSession,
    ) {
        val currentResult = AuthenticationManager.promptResults.value
        if (
            lifecycleOwner.lifecycle.currentState.isAtLeast(
                Lifecycle.State.STARTED
            ) &&
                !AuthenticationManager.hasValidSession() &&
                (currentResult ==
                    AuthenticationManager.AuthenticationResult.Loading ||
                    currentResult ==
                        AuthenticationManager.AuthenticationResult
                            .AuthenticationSuccess ||
                    currentResult ==
                        AuthenticationManager.AuthenticationResult
                            .AuthenticationNotSet)
        ) {
            showAuthPrompt(context)
        }
    }

    when (authenticationResult) {
        is AuthenticationManager.AuthenticationResult.Loading,
        is AuthenticationManager.AuthenticationResult.Pending -> {
            LoadingIndicator(stringResource(R.string.ui_waiting_for_authentication))
        }
        is AuthenticationManager.AuthenticationResult.AuthenticationSuccess,
        is AuthenticationManager.AuthenticationResult.AuthenticationNotSet -> {
            if (hasValidSession) {
                onAuthenticated()
            } else {
                LoadingIndicator(
                    stringResource(R.string.ui_waiting_for_authentication)
                )
            }
        }
        else -> onFailed(authenticationResult)
    }
}
