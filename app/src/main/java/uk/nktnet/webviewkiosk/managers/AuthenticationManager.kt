package com.nktnet.webview_kiosk.managers

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings

object AuthenticationManager {
    private var activity: AppCompatActivity? = null

    private val _resultState = MutableStateFlow<AuthenticationResult>(
        AuthenticationResult.Loading
    )
    val promptResults: StateFlow<AuthenticationResult?> = _resultState.asStateFlow()

    val showCustomAuth: MutableState<Boolean> = mutableStateOf(false)

    private var lastAuthTime = 0L
    private const val AUTH_TIMEOUT_MS = 5 * 60 * 1000L

    fun init(activity: AppCompatActivity) {
        this.activity = activity
    }

    fun checkAuthAndRefreshSession(): Boolean {
        val now = System.currentTimeMillis()
        val isValid = now - lastAuthTime < AUTH_TIMEOUT_MS
        if (isValid) {
            lastAuthTime = now
        }
        return isValid
    }

    fun resetAuthentication() {
        lastAuthTime = 0
    }

    fun showAuthenticationPrompt(
        title: String,
        description: String,
    ) {
        val activity = this.activity ?: run {
            _resultState.value = AuthenticationResult.AuthenticationError("Activity is null")
            return
        }

        _resultState.value = AuthenticationResult.Loading

        val customAuthPassword = UserSettings(activity).customAuthPassword
        if (customAuthPassword.isNotEmpty()) {
            showCustomAuthPrompt()
            return
        }

        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val deviceSecure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguardManager.isDeviceSecure
        } else {
            @Suppress("DEPRECATION")
            keyguardManager.isKeyguardSecure
        }

        if (!deviceSecure) {
            _resultState.value = AuthenticationResult.AuthenticationNotSet
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            showBiometricPromptModern(title, description)
        } else {
            showDeviceCredentialLollipop(keyguardManager, title, description)
        }
    }

    private fun showBiometricPromptModern(title: String, description: String) {
        val activity = this.activity ?: return
        val manager = BiometricManager.from(activity)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_STRONG
        }

        val canAuthenticate = manager.canAuthenticate(authenticators)
        if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            _resultState.value = AuthenticationResult.AuthenticationNotSet
            return
        }

        val promptInfoBuilder = PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(authenticators)
        } else {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        val prompt = BiometricPrompt(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    _resultState.value = AuthenticationResult.AuthenticationError(errString.toString())
                    resetAuthentication()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    _resultState.value = AuthenticationResult.AuthenticationSuccess
                    lastAuthTime = System.currentTimeMillis()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    _resultState.value = AuthenticationResult.AuthenticationFailed
                    resetAuthentication()
                }
            }
        )

        prompt.authenticate(promptInfoBuilder.build())
    }

    private fun showDeviceCredentialLollipop(keyguardManager: KeyguardManager, title: String, description: String) {
        val activity = this.activity ?: return
        try {
            @Suppress("DEPRECATION")
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(title, description)
            if (intent != null) {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, Constants.REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL)
            } else {
                _resultState.value = AuthenticationResult.AuthenticationError("Failed to create device credential intent")
            }
        } catch (e: Exception) {
            _resultState.value = AuthenticationResult.AuthenticationError(e.toString())
        }
    }

    fun handleLollipopDeviceCredentialResult(requestCode: Int, resultCode: Int) {
        if (requestCode != Constants.REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL) {
            return
        }
        if (resultCode == AppCompatActivity.RESULT_OK) {
            _resultState.value = AuthenticationResult.AuthenticationSuccess
            lastAuthTime = System.currentTimeMillis()
        } else {
            _resultState.value = AuthenticationResult.AuthenticationFailed
            resetAuthentication()
        }
    }

    fun showCustomAuthPrompt() {
        showCustomAuth.value = true
    }

    fun hideCustomAuthPrompt() {
        showCustomAuth.value = false
    }

    fun customAuthSuccess() {
        _resultState.value = AuthenticationResult.AuthenticationSuccess
        lastAuthTime = System.currentTimeMillis()
        hideCustomAuthPrompt()
    }

    fun customAuthCancel() {
        _resultState.value = AuthenticationResult.AuthenticationError("Authentication cancelled.")
        resetAuthentication()
        hideCustomAuthPrompt()
    }

    sealed interface AuthenticationResult {
        data object Loading : AuthenticationResult
        data class AuthenticationError(val error: String) : AuthenticationResult
        data object AuthenticationFailed : AuthenticationResult
        data object AuthenticationSuccess : AuthenticationResult
        data object AuthenticationNotSet : AuthenticationResult
    }
}
