package com.nktnet.webview_kiosk.auth

import android.app.KeyguardManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BiometricPromptManager(
    private val activity: AppCompatActivity
) {
    private val _resultState = MutableStateFlow<BiometricResult?>(null)
    val promptResults: StateFlow<BiometricResult?> = _resultState.asStateFlow()

    private var lastAuthTime = 0L
    private val authTimeoutMs = 2 * 60 * 1000L

    val isAuthenticated: Boolean
        get() = System.currentTimeMillis() - lastAuthTime < authTimeoutMs

    fun resetAuthentication() {
        lastAuthTime = 0
    }

    fun showBiometricPrompt(
        title: String,
        description: String
    ) {
        val keyguardManager = activity.getSystemService(KeyguardManager::class.java)
        if (keyguardManager == null || !keyguardManager.isDeviceSecure) {
            _resultState.value = BiometricResult.AuthenticationNotSet
            return
        }

        val manager = BiometricManager.from(activity)
        val authenticators = if (Build.VERSION.SDK_INT >= 30) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else BIOMETRIC_STRONG

        val promptInfoBuilder = PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)
            .setAllowedAuthenticators(authenticators)

        if (Build.VERSION.SDK_INT < 30) {
            promptInfoBuilder.setNegativeButtonText("Cancel")
        }

        when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                _resultState.value = BiometricResult.HardwareUnavailable
                return
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                _resultState.value = BiometricResult.FeatureUnavailable
                return
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                _resultState.value = BiometricResult.AuthenticationNotSet
                return
            }
            else -> Unit
        }

        val promptInfo = promptInfoBuilder.build()

        val prompt = BiometricPrompt(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    _resultState.value = BiometricResult.AuthenticationError(errString.toString())
                    resetAuthentication()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    _resultState.value = BiometricResult.AuthenticationSuccess
                    lastAuthTime = System.currentTimeMillis()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    _resultState.value = BiometricResult.AuthenticationFailed
                    resetAuthentication()
                }
            }
        )
        prompt.authenticate(promptInfo)
    }

    sealed interface BiometricResult {
        data object HardwareUnavailable : BiometricResult
        data object FeatureUnavailable : BiometricResult
        data class AuthenticationError(val error: String) : BiometricResult
        data object AuthenticationFailed : BiometricResult
        data object AuthenticationSuccess : BiometricResult
        data object AuthenticationNotSet : BiometricResult
    }
}
