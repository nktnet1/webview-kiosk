package uk.nktnet.webviewkiosk.auth

import android.app.KeyguardManager
import android.content.Context
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

object BiometricPromptManager {
    private var activity: AppCompatActivity? = null

    private val _resultState = MutableStateFlow<BiometricResult?>(null)
    val promptResults: StateFlow<BiometricResult?> = _resultState.asStateFlow()

    private var lastAuthTime = 0L
    private const val AUTH_TIMEOUT_MS = 5 * 60 * 1000L

    // Arbitrarily chosen number for request code.
    private const val LOLLIPOP_DEVICE_CREDENTIAL_REQUEST_CODE = 9999

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

    fun showBiometricPrompt(
        title: String,
        description: String
    ) {
        val activity = this.activity ?: run {
            _resultState.value = BiometricResult.AuthenticationError("Activity is null")
            return
        }

        _resultState.value = BiometricResult.Loading

        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val deviceSecure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguardManager.isDeviceSecure
        } else {
            @Suppress("DEPRECATION")
            keyguardManager.isKeyguardSecure
        }

        if (!deviceSecure) {
            _resultState.value = BiometricResult.AuthenticationNotSet
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
        } else BIOMETRIC_STRONG

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

        val promptInfoBuilder = PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)
            .setAllowedAuthenticators(authenticators)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            promptInfoBuilder.setNegativeButtonText("Cancel")
        }

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

        prompt.authenticate(promptInfoBuilder.build())
    }

    private fun showDeviceCredentialLollipop(keyguardManager: KeyguardManager, title: String, description: String) {
        val activity = this.activity ?: return
        try {
            @Suppress("DEPRECATION")
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(title, description)
            if (intent != null) {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, LOLLIPOP_DEVICE_CREDENTIAL_REQUEST_CODE)
            } else {
                _resultState.value = BiometricResult.FeatureUnavailable
            }
        } catch (_: Exception) {
            _resultState.value = BiometricResult.FeatureUnavailable
        }
    }

    fun handleLollipopDeviceCredentialResult(requestCode: Int, resultCode: Int) {
        if (requestCode != LOLLIPOP_DEVICE_CREDENTIAL_REQUEST_CODE) {
            return
        }
        if (resultCode == AppCompatActivity.RESULT_OK) {
            _resultState.value = BiometricResult.AuthenticationSuccess
            lastAuthTime = System.currentTimeMillis()
        } else {
            _resultState.value = BiometricResult.AuthenticationFailed
            resetAuthentication()
        }
    }

    sealed interface BiometricResult {
        data object Loading : BiometricResult
        data object HardwareUnavailable : BiometricResult
        data object FeatureUnavailable : BiometricResult
        data class AuthenticationError(val error: String) : BiometricResult
        data object AuthenticationFailed : BiometricResult
        data object AuthenticationSuccess : BiometricResult
        data object AuthenticationNotSet : BiometricResult
    }
}
