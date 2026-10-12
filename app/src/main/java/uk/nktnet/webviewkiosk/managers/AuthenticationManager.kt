package uk.nktnet.webviewkiosk.managers

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
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
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "WebViewKioskBiometricKey"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    private var encryptedAuthToken: ByteArray? = null
    private var encryptedAuthTokenIv: ByteArray? = null
    private const val BYPASS_AUTH_WINDOW_MS = 5000L

    private var activity: AppCompatActivity? = null

    private val _resultState = MutableStateFlow<AuthenticationResult>(
        AuthenticationResult.Loading
    )
    val promptResults: StateFlow<AuthenticationResult?> = _resultState.asStateFlow()

    val showCustomAuth: MutableState<Boolean> = mutableStateOf(false)

    internal var session = AuthenticationSession(SystemClock::elapsedRealtime)
    internal var authCipherFactory: (Pair<ByteArray, ByteArray>?) -> Cipher = ::createAuthCipher
    internal var authKeyInvalidator: () -> Unit = ::deleteAuthKey
    internal var biometricPromptFactory:
        (AppCompatActivity, BiometricPrompt.AuthenticationCallback) -> AuthenticationPrompt =
        ::AndroidAuthenticationPrompt

    internal interface AuthenticationPrompt {
        fun authenticate(info: PromptInfo, cryptoObject: BiometricPrompt.CryptoObject?)
        fun cancelAuthentication()
    }

    private class AndroidAuthenticationPrompt(
        activity: AppCompatActivity,
        callback: BiometricPrompt.AuthenticationCallback
    ) : AuthenticationPrompt {
        private val prompt = BiometricPrompt(activity, callback)

        override fun authenticate(info: PromptInfo, cryptoObject: BiometricPrompt.CryptoObject?) {
            if (cryptoObject == null) {
                prompt.authenticate(info)
            } else {
                prompt.authenticate(info, cryptoObject)
            }
        }

        override fun cancelAuthentication() = prompt.cancelAuthentication()
    }

    private enum class PromptKind { CUSTOM, BIOMETRIC, DEVICE_CREDENTIAL }

    private class PendingAuthentication(val kind: PromptKind) {
        var cancel: (() -> Unit)? = null
        var credentialRequestCode: Int? = null
    }

    private var pendingAuthentication: PendingAuthentication? = null
    private var nextCredentialRequestCode = Constants.REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL

    fun init(activity: AppCompatActivity) {
        this.activity = activity
    }

    fun clear(activity: AppCompatActivity) {
        if (this.activity === activity) {
            this.activity = null
            resetAuthentication()
        }
    }

    fun hasValidSession(): Boolean {
        if (
            _resultState.value != AuthenticationResult.AuthenticationSuccess
            && _resultState.value != AuthenticationResult.AuthenticationNotSet
        ) {
            return false
        }
        return session.isValid()
    }

    fun checkAuthAndRefreshSession(): Boolean {
        if (!hasValidSession()) {
            session.reset()
            return false
        }
        return session.refreshIfValid()
    }

    fun resetAuthentication(preserveExternalActivitySession: Boolean = false) {
        session.reset(preserveExternalActivitySession && hasValidSession())
        if (!preserveExternalActivitySession) {
            val pending = pendingAuthentication
            // Invalidate the callback before cancellation, which can deliver an error inline.
            pendingAuthentication = null
            hideCustomAuthPrompt()
            try {
                pending?.cancel?.invoke()
            } catch (e: Exception) {
                Log.e(javaClass.simpleName, "Failed to cancel authentication prompt.", e)
            }
        }
        if (
            !hasValidSession()
            && (_resultState.value == AuthenticationResult.AuthenticationSuccess
                || _resultState.value == AuthenticationResult.AuthenticationNotSet
                || (!preserveExternalActivitySession
                    && _resultState.value == AuthenticationResult.Pending))
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
        val activity = this.activity ?: run {
            _resultState.value = AuthenticationResult.AuthenticationError("Activity is null")
            return
        }

        val customAuthPassword = UserSettings(activity).customAuthPassword
        if (customAuthPassword.isNotEmpty()) {
            pendingAuthentication = PendingAuthentication(PromptKind.CUSTOM)
            _resultState.value = AuthenticationResult.Pending
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
            session.authenticate()
            _resultState.value = AuthenticationResult.AuthenticationNotSet
            return
        }

        val pending = PendingAuthentication(
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
                showDeviceCredentialLollipop(activity, keyguardManager, title, description, pending)
            }
        } catch (e: Exception) {
            handleAuthError(pending, "Failed to show authentication prompt: ${e.message}")
            try {
                pending.cancel?.invoke()
            } catch (cancelError: Exception) {
                Log.e(javaClass.simpleName, "Failed to cancel authentication prompt.", cancelError)
            }
        }
    }

    private fun handleAuthSuccess(pending: PendingAuthentication) {
        if (pendingAuthentication !== pending) {
            return
        }
        pendingAuthentication = null
        session.authenticate()
        _resultState.value = AuthenticationResult.AuthenticationSuccess
        hideCustomAuthPrompt()
    }

    private fun handleAuthError(pending: PendingAuthentication, error: String) {
        if (pendingAuthentication !== pending) {
            return
        }
        pendingAuthentication = null
        session.reset()
        _resultState.value = AuthenticationResult.AuthenticationError(error)
        hideCustomAuthPrompt()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun showBiometricPromptModern(
        activity: AppCompatActivity,
        title: String,
        description: String,
        pending: PendingAuthentication
    ) {
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_STRONG
        }

        // The device is secure. Let the prompt offer its PIN/pattern/password fallback
        // even when there are no enrolled biometrics (including Android 6-10).
        val promptInfoBuilder = PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(authenticators)
        } else {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        var existingToken = encryptedAuthToken?.let { token ->
            encryptedAuthTokenIv?.let { iv ->
                token to iv
            }
        }

        val prompt = biometricPromptFactory(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    handleAuthError(pending, errString.toString())
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    if (pendingAuthentication !== pending) {
                        return
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val cipher = result.cryptoObject?.cipher

                        if (cipher == null) {
                            handleAuthError(pending, "Missing cryptographic context.")
                            return
                        }

                        val token = existingToken
                        try {
                            if (token == null) {
                                encryptedAuthToken = cipher.doFinal(
                                    "auth-token".toByteArray(Charsets.UTF_8)
                                )
                                encryptedAuthTokenIv = cipher.iv
                            } else {
                                cipher.doFinal(token.first)
                            }
                            handleAuthSuccess(pending)
                        } catch (e: Exception) {
                            if (e is KeyPermanentlyInvalidatedException) {
                                try {
                                    clearInvalidatedAuthKey()
                                } catch (cleanupError: Exception) {
                                    Log.e(javaClass.simpleName, "Failed to remove invalidated key.", cleanupError)
                                }
                            }
                            Log.e(
                                javaClass.simpleName,
                                "Secure authentication validation failed.",
                                e
                            )
                            handleAuthError(pending, "Secure authentication validation failed.")
                        }
                    } else {
                        handleAuthSuccess(pending)
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // An unrecognised scan is retryable; the same native prompt remains open.
                }
            }
        )
        pending.cancel = prompt::cancelAuthentication

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val decryptCipher = try {
                try {
                    authCipherFactory(existingToken)
                } catch (_: KeyPermanentlyInvalidatedException) {
                    // The old token cannot be used with a replacement key. Prepare encryption
                    // for a new token, but still require a fresh authentication prompt.
                    clearInvalidatedAuthKey()
                    existingToken = null
                    authCipherFactory(null)
                }
            } catch (e: Exception) {
                Log.e(javaClass.simpleName, "Failed to create cipher.", e)
                handleAuthError(pending, "Failed to create cipher: ${e.message}")
                ToastManager.show(activity, "Failed to create cipher: ${e.message}")
                return
            }
            prompt.authenticate(
                promptInfoBuilder.build(),
                BiometricPrompt.CryptoObject(decryptCipher)
            )
        } else {
            prompt.authenticate(
                promptInfoBuilder.build(),
                null,
            )
        }
    }

    private fun getCipher(): Cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createAuthCipher(token: Pair<ByteArray, ByteArray>?): Cipher {
        return getCipher().apply {
            if (token == null) {
                init(Cipher.ENCRYPT_MODE, generateOrGetSecretKey())
            } else {
                init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, token.second))
            }
        }
    }

    private fun clearInvalidatedAuthKey() {
        encryptedAuthToken = null
        encryptedAuthTokenIv = null
        authKeyInvalidator()
    }

    private fun deleteAuthKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
    }

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
        activity: AppCompatActivity,
        keyguardManager: KeyguardManager,
        title: String,
        description: String,
        pending: PendingAuthentication
    ) {
        try {
            @Suppress("DEPRECATION")
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(title, description)
            if (intent != null) {
                // A result from a credential activity opened before a lock must not
                // authenticate a newer request. Activity request codes use 16 bits.
                val requestCode = nextCredentialRequestCode
                nextCredentialRequestCode = if (requestCode == 0xffff) {
                    Constants.REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL
                } else {
                    requestCode + 1
                }
                pending.credentialRequestCode = requestCode
                @Suppress("DEPRECATION")
                activity.startActivityForResult(
                    intent,
                    requestCode
                )
            } else {
                handleAuthError(pending, "Failed to create device credential intent")
            }
        } catch (e: Exception) {
            handleAuthError(pending, e.toString())
        }
    }

    fun handleLollipopDeviceCredentialResult(requestCode: Int, resultCode: Int) {
        val pending = pendingAuthentication
            ?.takeIf {
                it.kind == PromptKind.DEVICE_CREDENTIAL
                    && it.credentialRequestCode == requestCode
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

    fun customAuthSuccess() {
        val pending = pendingAuthentication
            ?.takeIf { it.kind == PromptKind.CUSTOM } ?: return
        handleAuthSuccess(pending)
    }

    fun customAuthCancel() {
        val pending = pendingAuthentication
            ?.takeIf { it.kind == PromptKind.CUSTOM } ?: return
        handleAuthError(pending, "Authentication cancelled.")
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
