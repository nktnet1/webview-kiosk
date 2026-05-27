package uk.nktnet.webviewkiosk.managers

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.annotation.RequiresApi
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
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AuthenticationManager {
    private const val AUTH_TIMEOUT_MS = 5 * 60 * 1000L
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "WebViewKioskBiometricKey"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    private var encryptedAuthToken: ByteArray? = null
    private var encryptedAuthTokenIv: ByteArray? = null
    private const val SKIP_RESET_WINDOW_MS = 5000L

    private var activity: AppCompatActivity? = null

    private val _resultState = MutableStateFlow<AuthenticationResult>(
        AuthenticationResult.Loading
    )
    val promptResults: StateFlow<AuthenticationResult?> = _resultState.asStateFlow()

    val showCustomAuth: MutableState<Boolean> = mutableStateOf(false)

    private var lastAuthTime = 0L
    private var skipResetUntil: Long = 0L

    fun init(activity: AppCompatActivity) {
        this.activity = activity
    }

    fun checkAuthAndRefreshSession(): Boolean {
        val now = System.currentTimeMillis()
        val isValid = now - lastAuthTime < AUTH_TIMEOUT_MS
        if (isValid) {
            lastAuthTime = now
        }
        skipResetUntil = 0L
        return isValid
    }

    fun resetAuthentication() {
        val now = System.currentTimeMillis()
        if (now <= skipResetUntil) {
            skipResetUntil = 0L
            return
        }
        lastAuthTime = 0
    }

    fun skipNextAuthResetForWindow() {
        val now = System.currentTimeMillis()
        skipResetUntil = now + SKIP_RESET_WINDOW_MS
        lastAuthTime = now
    }

    fun showAuthenticationPrompt(
        title: String,
        description: String,
    ) {
        val activity = this.activity ?: run {
            _resultState.value = AuthenticationResult.AuthenticationError("Activity is null")
            return
        }

        _resultState.value = AuthenticationResult.Pending

        val customAuthPassword = UserSettings(activity).customAuthPassword
        if (customAuthPassword.isNotEmpty()) {
            showCustomAuthPrompt()
            return
        }

        val keyguardManager = activity.getSystemService(
            Context.KEYGUARD_SERVICE
        ) as KeyguardManager

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

    private fun handleAuthSuccess() {
        _resultState.value = AuthenticationResult.AuthenticationSuccess
        lastAuthTime = System.currentTimeMillis()
        skipNextAuthResetForWindow()
    }

    @RequiresApi(Build.VERSION_CODES.M)
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

        val existingToken = encryptedAuthToken?.let { token ->
            encryptedAuthTokenIv?.let { iv ->
                token to iv
            }
        }

        val prompt = BiometricPrompt(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    _resultState.value = AuthenticationResult.AuthenticationError(
                        errString.toString()
                    )
                    resetAuthentication()
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val cipher = result.cryptoObject?.cipher

                        if (cipher == null) {
                            _resultState.value = AuthenticationResult.AuthenticationError(
                                "Missing cryptographic context."
                            )
                            resetAuthentication()
                            return
                        }

                        try {
                            if (existingToken == null) {
                                encryptedAuthToken = cipher.doFinal(
                                    "auth-token".toByteArray(Charsets.UTF_8)
                                )
                                encryptedAuthTokenIv = cipher.iv
                            } else {
                                cipher.doFinal(existingToken.first)
                            }
                            handleAuthSuccess()
                        } catch (e: Exception) {
                            Log.e(
                                javaClass.simpleName,
                                "Secure authentication validation failed.",
                                e
                            )
                            _resultState.value =
                                AuthenticationResult.AuthenticationError(
                                    "Secure authentication validation failed."
                                )
                            resetAuthentication()
                        }
                    } else {
                        handleAuthSuccess()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    _resultState.value = AuthenticationResult.AuthenticationFailed
                    resetAuthentication()
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val decryptCipher = try {
                val cipher = getCipher()

                if (existingToken == null) {
                    cipher.init(Cipher.ENCRYPT_MODE, generateOrGetSecretKey())
                } else {
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        getSecretKey(),
                        GCMParameterSpec(128, existingToken.second)
                    )
                }

                cipher
            } catch (e: Exception) {
                Log.e(javaClass.simpleName, "Failed to create cipher.", e)
                _resultState.value = AuthenticationResult.AuthenticationError(
                    "Failed to create cipher: ${e.message}"
                )
                ToastManager.show(activity, "Failed to create cipher: ${e.message}")
                resetAuthentication()
                return
            }
            prompt.authenticate(
                promptInfoBuilder.build(),
                BiometricPrompt.CryptoObject(decryptCipher)
            )
        } else {
            prompt.authenticate(
                promptInfoBuilder.build(),
            )
        }
    }

    private fun getCipher(): Cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)

    @RequiresApi(Build.VERSION_CODES.M)
    private fun generateOrGetSecretKey(): SecretKey {
        return try {
            getSecretKey()
        } catch (_: Exception) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keySpecBuilder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                keySpecBuilder.setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                        or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            }

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
            ) {
                keySpecBuilder.setInvalidatedByBiometricEnrollment(true)
            }

            keyGenerator.init(keySpecBuilder.build())
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey
    }

    private fun showDeviceCredentialLollipop(
        keyguardManager: KeyguardManager,
        title: String,
        description: String
    ) {
        val activity = this.activity ?: return
        try {
            @Suppress("DEPRECATION")
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(title, description)
            if (intent != null) {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(
                    intent,
                    Constants.REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL
                )
            } else {
                _resultState.value = AuthenticationResult.AuthenticationError(
                    "Failed to create device credential intent"
                )
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
        data object Pending : AuthenticationResult
        data class AuthenticationError(val error: String) : AuthenticationResult
        data object AuthenticationFailed : AuthenticationResult
        data object AuthenticationSuccess : AuthenticationResult
        data object AuthenticationNotSet : AuthenticationResult
    }
}
