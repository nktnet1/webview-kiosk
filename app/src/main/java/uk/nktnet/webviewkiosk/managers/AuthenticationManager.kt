package uk.nktnet.webviewkiosk.managers

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings

object AuthenticationManager {
    private const val BYPASS_AUTH_WINDOW_MS = 5000L

    private var activity: AppCompatActivity? = null

    private val _resultState =
        MutableStateFlow<AuthenticationResult>(AuthenticationResult.Loading)
    val promptResults: StateFlow<AuthenticationResult?> = _resultState.asStateFlow()

    val showCustomAuth: MutableState<Boolean> = mutableStateOf(false)
    // Each custom dialog must complete only the request that displayed it. This
    // observable token also causes Compose to discard credentials from an old
    // dialog even when reset/retry happens between two recompositions.
    private val _customAuthRequest = mutableStateOf<Any?>(null)
    val customAuthRequest: State<Any?> = _customAuthRequest

    internal var session = AuthenticationSession(SystemClock::elapsedRealtime)
    internal var biometricPromptFactory:
        (
            AppCompatActivity,
            BiometricPrompt.AuthenticationCallback,
        ) -> AuthenticationPrompt =
        ::AndroidAuthenticationPrompt

    internal interface AuthenticationPrompt {
        fun authenticate(info: PromptInfo)

        fun cancelAuthentication()
    }

    private class AndroidAuthenticationPrompt(
        activity: AppCompatActivity,
        callback: BiometricPrompt.AuthenticationCallback,
    ) : AuthenticationPrompt {
        private val prompt = BiometricPrompt(activity, callback)

        override fun authenticate(info: PromptInfo) = prompt.authenticate(info)

        override fun cancelAuthentication() = prompt.cancelAuthentication()
    }

    private enum class PromptKind {
        CUSTOM,
        BIOMETRIC,
        DEVICE_CREDENTIAL,
    }

    private class PendingAuthentication(val kind: PromptKind) {
        var cancel: (() -> Unit)? = null
        var credentialRequestCode: Int? = null
    }

    private var pendingAuthentication: PendingAuthentication? = null
    private var nextCredentialRequestCode =
        Constants.REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL

    fun init(activity: AppCompatActivity) {
        // An unfinished prompt belongs to its original host. Its callbacks may
        // never arrive after activity replacement, blocking the successor's prompt.
        // Invalidate before cancellation, which can itself call back inline.
        if (
            this.activity != null &&
                this.activity !== activity &&
                pendingAuthentication != null
        ) {
            resetAuthentication()
        }
        this.activity = activity
    }

    fun clear(activity: AppCompatActivity) {
        if (this.activity === activity) {
            this.activity = null
            resetAuthentication()
        }
    }

    fun hasValidSession(): Boolean =
        (_resultState.value == AuthenticationResult.AuthenticationSuccess ||
            _resultState.value == AuthenticationResult.AuthenticationNotSet) &&
            session.isValid()

    fun checkAuthAndRefreshSession(): Boolean {
        if (!hasValidSession() || !session.refreshIfValid()) {
            session.reset()
            // Session time is not observable Compose state. Expiration must
            // invalidate the success result as well as the clock so the auth
            // wrapper can reliably start a fresh prompt after resuming.
            if (
                _resultState.value == AuthenticationResult.AuthenticationSuccess ||
                    _resultState.value == AuthenticationResult.AuthenticationNotSet
            ) {
                _resultState.value = AuthenticationResult.Loading
            }
            return false
        }
        return true
    }

    fun resetAuthentication(preserveExternalActivitySession: Boolean = false) {
        session.reset(preserveExternalActivitySession && hasValidSession())
        if (!preserveExternalActivitySession) {
            val pending = pendingAuthentication
            // Invalidate the callback before cancellation, which can deliver an
            // error inline.
            pendingAuthentication = null
            _customAuthRequest.value = null
            hideCustomAuthPrompt()
            try {
                pending?.cancel?.invoke()
            } catch (e: Exception) {
                Log.e(
                    javaClass.simpleName,
                    "Failed to cancel authentication prompt.",
                    e,
                )
            }
        }
        if (
            !hasValidSession() &&
                (_resultState.value == AuthenticationResult.AuthenticationSuccess ||
                    _resultState.value ==
                        AuthenticationResult.AuthenticationNotSet ||
                    (!preserveExternalActivitySession &&
                        _resultState.value == AuthenticationResult.Pending))
        ) {
            _resultState.value = AuthenticationResult.Loading
        }
    }

    fun bypassAuthForWindow(durationMs: Long = BYPASS_AUTH_WINDOW_MS) {
        // Preserve an authenticated session during an external activity round trip.
        // Opening an external app must not establish a session by itself.
        if (!hasValidSession()) {
            return
        }
        session.preserveForExternalActivity(durationMs)
    }

    fun showAuthenticationPrompt(
        title: String,
        description: String,
    ) {
        if (pendingAuthentication != null) {
            return
        }
        val activity =
            this.activity
                ?: run {
                    _resultState.value =
                        AuthenticationResult.AuthenticationError("Activity is null")
                    return
                }

        val customAuthPassword = UserSettings(activity).customAuthPassword
        if (customAuthPassword.isNotEmpty()) {
            val pending = PendingAuthentication(PromptKind.CUSTOM)
            pendingAuthentication = pending
            _customAuthRequest.value = pending
            _resultState.value = AuthenticationResult.Pending
            showCustomAuthPrompt()
            return
        }

        val keyguardManager =
            activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        val deviceSecure =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                keyguardManager.isDeviceSecure
            } else {
                @Suppress("DEPRECATION") keyguardManager.isKeyguardSecure
            }

        if (!deviceSecure) {
            session.authenticate()
            _resultState.value = AuthenticationResult.AuthenticationNotSet
            return
        }

        val pending =
            PendingAuthentication(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PromptKind.BIOMETRIC
                } else {
                    PromptKind.DEVICE_CREDENTIAL
                }
            )
        pendingAuthentication = pending
        _resultState.value = AuthenticationResult.Pending

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                showBiometricPromptModern(activity, title, description, pending)
            } else {
                showDeviceCredentialLollipop(
                    activity,
                    keyguardManager,
                    title,
                    description,
                    pending,
                )
            }
        } catch (e: Exception) {
            handleAuthError(
                pending,
                "Failed to show authentication prompt: ${e.message}",
            )
            try {
                pending.cancel?.invoke()
            } catch (cancelError: Exception) {
                Log.e(
                    javaClass.simpleName,
                    "Failed to cancel authentication prompt.",
                    cancelError,
                )
            }
        }
    }

    private fun handleAuthSuccess(pending: PendingAuthentication) {
        if (pendingAuthentication !== pending) {
            return
        }
        pendingAuthentication = null
        _customAuthRequest.value = null
        session.authenticate()
        _resultState.value = AuthenticationResult.AuthenticationSuccess
        hideCustomAuthPrompt()
    }

    private fun handleAuthError(pending: PendingAuthentication, error: String) {
        if (pendingAuthentication !== pending) {
            return
        }
        pendingAuthentication = null
        _customAuthRequest.value = null
        session.reset()
        _resultState.value = AuthenticationResult.AuthenticationError(error)
        hideCustomAuthPrompt()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun showBiometricPromptModern(
        activity: AppCompatActivity,
        title: String,
        description: String,
        pending: PendingAuthentication,
    ) {
        val authenticators =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            } else {
                BIOMETRIC_STRONG
            }

        // The device is secure. Let the prompt offer its PIN/pattern/password
        // fallback
        // even when there are no enrolled biometrics (including Android 6-10).
        val promptInfoBuilder =
            PromptInfo.Builder().setTitle(title).setDescription(description)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(authenticators)
        } else {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        val prompt =
            biometricPromptFactory(
                activity,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                        handleAuthError(pending, errString.toString())
                    }

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        handleAuthSuccess(pending)
                    }
                },
            )
        pending.cancel = prompt::cancelAuthentication

        // This prompt gates the UI. No encrypted data needs a CryptoObject or
        // a per-user Keystore key, which can reject work-profile credentials.
        prompt.authenticate(promptInfoBuilder.build())
    }

    private fun showDeviceCredentialLollipop(
        activity: AppCompatActivity,
        keyguardManager: KeyguardManager,
        title: String,
        description: String,
        pending: PendingAuthentication,
    ) {
        try {
            @Suppress("DEPRECATION")
            val intent =
                keyguardManager.createConfirmDeviceCredentialIntent(
                    title,
                    description,
                )
            if (intent != null) {
                // A result from a credential activity opened before a lock must not
                // authenticate a newer request. Activity request codes use 16 bits.
                val requestCode = nextCredentialRequestCode
                nextCredentialRequestCode =
                    if (requestCode == 0xffff) {
                        Constants.REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL
                    } else {
                        requestCode + 1
                    }
                pending.credentialRequestCode = requestCode
                @Suppress("DEPRECATION")
                activity.startActivityForResult(
                    intent,
                    requestCode,
                )
            } else {
                handleAuthError(pending, "Failed to create device credential intent")
            }
        } catch (e: Exception) {
            handleAuthError(pending, e.toString())
        }
    }

    fun handleLollipopDeviceCredentialResult(requestCode: Int, resultCode: Int) {
        val pending =
            pendingAuthentication?.takeIf {
                it.kind == PromptKind.DEVICE_CREDENTIAL &&
                    it.credentialRequestCode == requestCode
            } ?: return
        if (resultCode == AppCompatActivity.RESULT_OK) {
            handleAuthSuccess(pending)
        } else {
            pendingAuthentication = null
            _resultState.value = AuthenticationResult.AuthenticationFailed
            session.reset()
        }
    }

    fun showCustomAuthPrompt() {
        if (pendingAuthentication?.kind == PromptKind.CUSTOM) {
            showCustomAuth.value = true
        }
    }

    fun hideCustomAuthPrompt() {
        showCustomAuth.value = false
    }

    fun isCurrentCustomAuthRequest(request: Any?): Boolean =
        request != null &&
            pendingAuthentication === request &&
            pendingAuthentication?.kind == PromptKind.CUSTOM

    fun customAuthSuccess(request: Any?) {
        if (!isCurrentCustomAuthRequest(request)) {
            return
        }
        handleAuthSuccess(pendingAuthentication ?: return)
    }

    fun customAuthCancel(request: Any?) {
        if (!isCurrentCustomAuthRequest(request)) {
            return
        }
        handleAuthError(pendingAuthentication ?: return, "Authentication cancelled.")
    }

    sealed interface AuthenticationResult {
        data object Loading : AuthenticationResult

        data object Pending : AuthenticationResult

        data class AuthenticationError(val error: String) : AuthenticationResult

        data object AuthenticationFailed : AuthenticationResult

        data object AuthenticationSuccess : AuthenticationResult

        data object AuthenticationNotSet : AuthenticationResult
    }
}
