package uk.nktnet.webviewkiosk.managers

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowKeyguardManager
import org.robolectric.util.ReflectionHelpers
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.AuthenticationManager.AuthenticationResult
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [CredentialKeyguardShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class AuthenticationManagerTest {
    private val manager = AuthenticationManager
    private val originalSession = manager.session
    private val originalPromptFactory = manager.biometricPromptFactory
    private val originalCipherFactory = manager.authCipherFactory
    private val originalKeyInvalidator = manager.authKeyInvalidator
    private val originalSdk = Build.VERSION.SDK_INT
    private lateinit var activityController: ActivityController<AuthTestActivity>
    private lateinit var activity: AuthTestActivity
    private lateinit var keyguardManager: KeyguardManager
    private val prompts = mutableListOf<FakePrompt>()
    private var time = 1_000L
    private var deletedKeys = 0
    private val secretKey = SecretKeySpec(ByteArray(16) { 7 }, "AES")

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(AuthTestActivity::class.java).setup()
        activity = activityController.get()
        activity.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguardManager).setIsDeviceSecure(true)
        shadowOf(keyguardManager).setIsKeyguardSecure(true)
        manager.resetAuthentication()
        manager.session = AuthenticationSession { time }
        manager.biometricPromptFactory = { _, callback ->
            FakePrompt(callback).also { prompts.add(it) }
        }
        manager.authCipherFactory = ::createCipher
        manager.authKeyInvalidator = { deletedKeys++ }
        setStoredToken(null, null)
        manager.init(activity)
    }

    @After
    fun tearDown() {
        manager.clear(activity)
        manager.session = originalSession
        manager.biometricPromptFactory = originalPromptFactory
        manager.authCipherFactory = originalCipherFactory
        manager.authKeyInvalidator = originalKeyInvalidator
        setStoredToken(null, null)
        setSdk(originalSdk)
        activityController.pause().stop().destroy()
    }

    @Test
    fun pendingAuthenticationDoesNotStartAnotherPrompt() {
        showPrompt()
        repeat(3) { showPrompt() }

        assertEquals(1, prompts.size)
        assertEquals(1, prompts.single().authenticateCalls)
        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertFalse(manager.hasValidSession())
    }

    @Test
    fun anUnrecognisedScanKeepsTheExistingPromptPendingUntilSuccess() {
        showPrompt()
        val prompt = prompts.single()
        repeat(2) { prompt.callback.onAuthenticationFailed() }
        showPrompt()

        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertEquals(1, prompts.size)
        assertFalse(manager.hasValidSession())

        prompt.succeed()
        assertEquals(AuthenticationResult.AuthenticationSuccess, manager.promptResults.value)
        assertTrue(manager.hasValidSession())
    }

    @Test
    fun aLockCancelsThePromptAndRejectsItsLateSuccess() {
        showPrompt()
        val prompt = prompts.single()
        manager.resetAuthentication()
        prompt.succeed()

        assertEquals(1, prompt.cancelCalls)
        assertEquals(AuthenticationResult.Loading, manager.promptResults.value)
        assertFalse(manager.hasValidSession())
        assertFalse(manager.checkAuthAndRefreshSession())
    }

    @Test
    fun callbacksFromAnOldPromptCannotCompleteOrFailItsReplacement() {
        showPrompt()
        val oldPrompt = prompts.single()
        manager.resetAuthentication()
        showPrompt()
        val replacement = prompts.last()

        oldPrompt.succeed()
        oldPrompt.callback.onAuthenticationError(1, "stale error")
        oldPrompt.callback.onAuthenticationFailed()

        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertFalse(manager.hasValidSession())
        assertEquals(1, replacement.authenticateCalls)
        assertEquals(0, replacement.cancelCalls)

        replacement.succeed()
        assertTrue(manager.hasValidSession())
    }

    @Test
    fun aDuplicateSuccessDoesNotRefreshTheSessionOrExtendItsTimeout() {
        showPrompt()
        val prompt = prompts.single()
        prompt.succeed()
        time += 299_999
        prompt.succeed()
        time++

        assertFalse(manager.hasValidSession())
    }

    @Test
    fun aTerminalErrorInvalidatesTheSessionAndAllowsExactlyOneRetry() {
        showPrompt()
        val prompt = prompts.single()
        prompt.callback.onAuthenticationError(1, "cancelled")
        prompt.succeed()

        assertEquals(AuthenticationResult.AuthenticationError("cancelled"), manager.promptResults.value)
        assertFalse(manager.hasValidSession())
        showPrompt()
        showPrompt()
        assertEquals(2, prompts.size)
        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
    }

    @Test
    fun backgroundingForTheCredentialUiDoesNotInvalidateThePendingPrompt() {
        showPrompt()
        val prompt = prompts.single()
        manager.resetAuthentication(preserveExternalActivitySession = true)

        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertEquals(0, prompt.cancelCalls)
        assertFalse(manager.hasValidSession())

        prompt.succeed()
        assertTrue(manager.hasValidSession())
    }

    @Test
    fun secureDevicesWithoutEnrolledBiometricsKeepCredentialFallbackOnAndroid6To10() {
        // Only exercise the SDK gates on the exported API 28 runtime. The fake prompt
        // does not invoke hardware or APIs from a different Android runtime.
        for (sdk in listOf(23, 28, 29)) {
            setSdk(sdk)
            showPrompt()
            val prompt = prompts.last()

            @Suppress("DEPRECATION")
            assertTrue(prompt.info.isDeviceCredentialAllowed)
            assertNull(prompt.cryptoObject)
            assertFalse(manager.hasValidSession())
            manager.resetAuthentication()
        }
        assertEquals(3, prompts.size)
    }

    @Test
    fun anUnsecuredDeviceGetsATimedSessionThatAnExplicitLockClears() {
        shadowOf(keyguardManager).setIsDeviceSecure(false)
        showPrompt()

        assertEquals(AuthenticationResult.AuthenticationNotSet, manager.promptResults.value)
        assertTrue(manager.hasValidSession())
        assertTrue(prompts.isEmpty())
        time += 300_000
        assertFalse(manager.hasValidSession())
        assertFalse(manager.checkAuthAndRefreshSession())
        showPrompt()
        assertTrue(manager.hasValidSession())
        manager.resetAuthentication()
        assertEquals(AuthenticationResult.Loading, manager.promptResults.value)
        assertFalse(manager.hasValidSession())
    }

    @Test
    fun anExternalRoundTripCannotCreateOrReviveAnUnauthenticatedSession() {
        manager.bypassAuthForWindow()
        manager.resetAuthentication(preserveExternalActivitySession = true)
        assertFalse(manager.hasValidSession())

        showPrompt()
        prompts.single().succeed()
        time += 300_000
        manager.bypassAuthForWindow()
        manager.resetAuthentication(preserveExternalActivitySession = true)
        assertFalse(manager.hasValidSession())
    }

    @Test
    fun aValidExternalRoundTripExpiresAtItsDeadlineAndCanBeConsumedOnlyOnce() {
        showPrompt()
        prompts.single().succeed()
        manager.bypassAuthForWindow()
        manager.resetAuthentication(preserveExternalActivitySession = true)
        time += 4_999
        assertTrue(manager.checkAuthAndRefreshSession())

        manager.resetAuthentication(preserveExternalActivitySession = true)
        assertFalse(manager.hasValidSession())

        showPrompt()
        prompts.last().succeed()
        manager.bypassAuthForWindow()
        manager.resetAuthentication(preserveExternalActivitySession = true)
        time += 5_000
        assertFalse(manager.hasValidSession())
        assertFalse(manager.checkAuthAndRefreshSession())
    }

    @Test
    fun aSessionTimestampWithoutASuccessfulResultDoesNotUnlock() {
        showPrompt()
        manager.session.authenticate()

        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertFalse(manager.hasValidSession())
        assertFalse(manager.checkAuthAndRefreshSession())
    }

    @Test
    fun aCustomPromptIsNotDuplicatedAndLateSuccessAfterLockIsIgnored() {
        UserSettings(activity).customAuthPassword = "1234"
        showPrompt()
        showPrompt()

        assertTrue(manager.showCustomAuth.value)
        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertTrue(prompts.isEmpty())

        manager.resetAuthentication()
        manager.customAuthSuccess()
        assertFalse(manager.showCustomAuth.value)
        assertFalse(manager.hasValidSession())
        assertEquals(AuthenticationResult.Loading, manager.promptResults.value)
    }

    @Test
    fun customAuthenticationSuccessAndCancellationHaveIndependentSessions() {
        UserSettings(activity).customAuthPassword = "1234"
        showPrompt()
        manager.customAuthSuccess()
        assertTrue(manager.hasValidSession())
        assertFalse(manager.showCustomAuth.value)

        showPrompt()
        manager.customAuthCancel()
        manager.customAuthSuccess()
        assertFalse(manager.hasValidSession())
        assertFalse(manager.showCustomAuth.value)
        assertEquals(
            AuthenticationResult.AuthenticationError("Authentication cancelled."),
            manager.promptResults.value
        )
    }

    @Test
    fun aCredentialResultWithoutAPendingLegacyPromptCannotUnlock() {
        manager.handleLollipopDeviceCredentialResult(
            Constants.REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL,
            AppCompatActivity.RESULT_OK
        )
        assertFalse(manager.hasValidSession())

        showPrompt()
        manager.handleLollipopDeviceCredentialResult(
            Constants.REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL,
            AppCompatActivity.RESULT_OK
        )
        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertFalse(manager.hasValidSession())
    }

    @Test
    fun legacyCredentialRoundTripIsLaunchedOnceAndMustSucceedBeforeUnlocking() {
        setSdk(22)
        showPrompt()
        showPrompt()
        assertEquals(1, activity.credentialRequests.size)
        val requestCode = activity.credentialRequests.single()
        assertFalse(manager.hasValidSession())
        manager.resetAuthentication(preserveExternalActivitySession = true)
        manager.handleLollipopDeviceCredentialResult(123, AppCompatActivity.RESULT_OK)
        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)

        manager.handleLollipopDeviceCredentialResult(
            requestCode,
            AppCompatActivity.RESULT_OK
        )
        assertTrue(manager.hasValidSession())
        time += 300_000
        manager.handleLollipopDeviceCredentialResult(
            requestCode,
            AppCompatActivity.RESULT_OK
        )
        assertFalse(manager.hasValidSession())
    }

    @Test
    fun legacyCancellationAndLateResultAfterALockDoNotAuthenticate() {
        setSdk(22)
        showPrompt()
        manager.handleLollipopDeviceCredentialResult(
            activity.credentialRequests.single(),
            AppCompatActivity.RESULT_CANCELED
        )
        assertEquals(AuthenticationResult.AuthenticationFailed, manager.promptResults.value)
        assertFalse(manager.hasValidSession())

        showPrompt()
        manager.resetAuthentication()
        manager.handleLollipopDeviceCredentialResult(
            activity.credentialRequests.last(),
            AppCompatActivity.RESULT_OK
        )
        assertEquals(AuthenticationResult.Loading, manager.promptResults.value)
        assertFalse(manager.hasValidSession())
    }

    @Test
    fun aLegacyResultFromBeforeALockCannotCompleteTheReplacementRequest() {
        setSdk(22)
        showPrompt()
        val oldRequestCode = activity.credentialRequests.single()
        manager.resetAuthentication()
        showPrompt()
        val currentRequestCode = activity.credentialRequests.last()
        assertTrue(oldRequestCode != currentRequestCode)

        manager.handleLollipopDeviceCredentialResult(oldRequestCode, AppCompatActivity.RESULT_OK)
        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertFalse(manager.hasValidSession())

        manager.handleLollipopDeviceCredentialResult(currentRequestCode, AppCompatActivity.RESULT_OK)
        assertTrue(manager.hasValidSession())
    }

    @Test
    fun clearingAnOldHostDoesNotCancelTheCurrentHostsPrompt() {
        showPrompt()
        val other = Robolectric.buildActivity(AuthTestActivity::class.java).setup()
        try {
            manager.clear(other.get())
            assertEquals(0, prompts.single().cancelCalls)
            prompts.single().succeed()
            assertTrue(manager.hasValidSession())
        } finally {
            other.pause().stop().destroy()
        }
    }

    @Test
    fun clearingTheCurrentHostCancelsItsPromptAndRejectsLateCallbacks() {
        showPrompt()
        val prompt = prompts.single()
        manager.clear(activity)
        prompt.succeed()

        assertEquals(1, prompt.cancelCalls)
        assertFalse(manager.hasValidSession())
        showPrompt()
        assertEquals(AuthenticationResult.AuthenticationError("Activity is null"), manager.promptResults.value)
    }

    @Test
    fun android11RequiresACipherAndAllowsBiometricsOrDeviceCredentials() {
        setSdk(30)
        showPrompt()
        val prompt = prompts.single()
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, prompt.info.allowedAuthenticators)
        assertTrue(prompt.cryptoObject?.cipher != null)
        assertFalse(manager.hasValidSession())

        prompt.succeed(cryptoObject = null)
        assertEquals(
            AuthenticationResult.AuthenticationError("Missing cryptographic context."),
            manager.promptResults.value
        )
        assertFalse(manager.hasValidSession())
    }

    @Test
    fun android11EncryptsThenValidatesTheStoredTokenAfterFreshAuthentication() {
        setSdk(30)
        val requestedTokens = mutableListOf<Pair<ByteArray, ByteArray>?>()
        manager.authCipherFactory = { token ->
            requestedTokens.add(token)
            createCipher(token)
        }

        showPrompt()
        prompts.single().succeed()
        assertTrue(manager.hasValidSession())
        manager.resetAuthentication()
        showPrompt()
        assertFalse(manager.hasValidSession())
        assertNull(requestedTokens.first())
        assertTrue(requestedTokens.last() != null)
        prompts.last().succeed()
        assertTrue(manager.hasValidSession())
    }

    @Test
    fun anInvalidatedKeyIsDeletedAndStillRequiresTheNewPromptToSucceed() {
        setSdk(30)
        showPrompt()
        prompts.single().succeed()
        manager.resetAuthentication()
        val requestedTokens = mutableListOf<Pair<ByteArray, ByteArray>?>()
        manager.authCipherFactory = { token ->
            requestedTokens.add(token)
            if (token != null) throw KeyPermanentlyInvalidatedException()
            createCipher(null)
        }

        showPrompt()
        assertEquals(1, deletedKeys)
        assertEquals(2, requestedTokens.size)
        assertTrue(requestedTokens.first() != null)
        assertNull(requestedTokens.last())
        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertFalse(manager.hasValidSession())
        assertEquals(1, prompts.last().authenticateCalls)

        prompts.last().succeed()
        assertTrue(manager.hasValidSession())
    }

    @Test
    fun invalidatedKeyCleanupFailureDoesNotLaunchOrCompleteAuthentication() {
        setSdk(30)
        manager.authCipherFactory = { throw KeyPermanentlyInvalidatedException() }
        manager.authKeyInvalidator = {
            deletedKeys++
            error("key deletion failed")
        }

        showPrompt()
        assertEquals(1, deletedKeys)
        assertEquals(0, prompts.single().authenticateCalls)
        assertTrue(manager.promptResults.value is AuthenticationResult.AuthenticationError)
        prompts.single().succeed()
        assertFalse(manager.hasValidSession())
    }

    @Test
    fun replacementCipherCreationFailureCannotUnlockOrLeaveAPendingPrompt() {
        setSdk(30)
        var calls = 0
        manager.authCipherFactory = {
            if (calls++ == 0) throw KeyPermanentlyInvalidatedException()
            error("replacement cipher failed")
        }

        showPrompt()
        assertEquals(2, calls)
        assertEquals(1, deletedKeys)
        assertEquals(0, prompts.single().authenticateCalls)
        assertTrue(manager.promptResults.value is AuthenticationResult.AuthenticationError)
        assertFalse(manager.hasValidSession())
    }

    @Test
    fun aCorruptedTokenFailsValidationInsteadOfUnlocking() {
        setSdk(30)
        showPrompt()
        prompts.single().succeed()
        manager.resetAuthentication()
        val token = getStoredBytes("encryptedAuthToken")!!.copyOf()
        token[0] = (token[0].toInt() xor 1).toByte()
        setStoredToken(token, getStoredBytes("encryptedAuthTokenIv"))

        showPrompt()
        prompts.last().succeed()
        assertEquals(
            AuthenticationResult.AuthenticationError("Secure authentication validation failed."),
            manager.promptResults.value
        )
        assertFalse(manager.hasValidSession())
    }

    @Test
    fun aLateCryptoCallbackDoesNotWriteATokenOrChangeTheReplacementPrompt() {
        setSdk(30)
        showPrompt()
        val oldPrompt = prompts.single()
        manager.resetAuthentication()
        showPrompt()
        val currentPrompt = prompts.last()
        oldPrompt.succeed()

        assertNull(getStoredBytes("encryptedAuthToken"))
        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertFalse(manager.hasValidSession())
        currentPrompt.succeed()
        assertTrue(manager.hasValidSession())
    }

    @Test
    fun promptStartupFailureAllowsRetryWithoutGrantingASession() {
        manager.biometricPromptFactory = { _, _ -> error("host unavailable") }
        showPrompt()
        assertTrue(manager.promptResults.value is AuthenticationResult.AuthenticationError)
        assertFalse(manager.hasValidSession())

        manager.biometricPromptFactory = { _, callback ->
            FakePrompt(callback).also { prompts.add(it) }
        }
        showPrompt()
        assertEquals(AuthenticationResult.Pending, manager.promptResults.value)
        assertEquals(1, prompts.single().authenticateCalls)
    }

    private fun showPrompt() = manager.showAuthenticationPrompt("Authentication Required", "Test authentication")

    private fun createCipher(token: Pair<ByteArray, ByteArray>?): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            if (token == null) {
                init(Cipher.ENCRYPT_MODE, secretKey)
            } else {
                init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, token.second))
            }
        }

    private fun setSdk(sdk: Int) = ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)

    private fun setStoredToken(token: ByteArray?, iv: ByteArray?) {
        for ((name, value) in listOf("encryptedAuthToken" to token, "encryptedAuthTokenIv" to iv)) {
            manager.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(null, value)
        }
    }

    private fun getStoredBytes(name: String): ByteArray? =
        manager.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(null) as ByteArray?

    private class FakePrompt(val callback: BiometricPrompt.AuthenticationCallback) :
        AuthenticationManager.AuthenticationPrompt {
        lateinit var info: BiometricPrompt.PromptInfo
        var cryptoObject: BiometricPrompt.CryptoObject? = null
        var authenticateCalls = 0
        var cancelCalls = 0

        override fun authenticate(info: BiometricPrompt.PromptInfo, cryptoObject: BiometricPrompt.CryptoObject?) {
            this.info = info
            this.cryptoObject = cryptoObject
            authenticateCalls++
        }

        override fun cancelAuthentication() {
            cancelCalls++
            callback.onAuthenticationError(BiometricPrompt.ERROR_CANCELED, "cancelled")
        }

        fun succeed(cryptoObject: BiometricPrompt.CryptoObject? = this.cryptoObject) {
            val result = ReflectionHelpers.callConstructor(
                BiometricPrompt.AuthenticationResult::class.java,
                ReflectionHelpers.ClassParameter.from(BiometricPrompt.CryptoObject::class.java, cryptoObject),
                ReflectionHelpers.ClassParameter.from(
                    Int::class.javaPrimitiveType!!,
                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC
                )
            )
            assertSame(cryptoObject, result.cryptoObject)
            callback.onAuthenticationSucceeded(result)
        }
    }
}

class AuthTestActivity : AppCompatActivity() {
    val credentialRequests = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        super.onCreate(savedInstanceState)
    }

    @Deprecated("Test legacy credential launch")
    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        credentialRequests.add(requestCode)
    }
}

@Implements(KeyguardManager::class)
class CredentialKeyguardShadow : ShadowKeyguardManager() {
    @Implementation
    protected fun createConfirmDeviceCredentialIntent(title: CharSequence, description: CharSequence): Intent =
        Intent("test.confirm-credential")
}
