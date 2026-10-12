package uk.nktnet.webviewkiosk.ui.components.auth

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.Text
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.managers.AuthenticationSession

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class RequireAuthWrapperTest {
    private val manager = AuthenticationManager
    private val originalSession = manager.session
    private val originalPromptFactory = manager.biometricPromptFactory
    private lateinit var controller: ActivityController<AuthWrapperTestActivity>
    private lateinit var activity: AuthWrapperTestActivity
    private lateinit var view: ComposeView
    private lateinit var navController: NavHostController
    private val frameClock = BroadcastFrameClock()
    private val compositionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + frameClock)
    private lateinit var recomposer: Recomposer
    private var frameNanos = 0L
    private val owner = WrapperLifecycleOwner()
    private val currentOwner = mutableStateOf(owner)
    private val showWrapper = mutableStateOf(true)
    private val prompts = mutableListOf<RecordingPrompt>()
    private val revision = mutableStateOf(0)
    private var time = 1_000L
    private var protectedMounts = 0
    private var protectedDisposals = 0

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(AuthWrapperTestActivity::class.java).setup().visible()
        activity = controller.get()
        activity.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguard).setIsDeviceSecure(true)
        manager.resetAuthentication()
        manager.session = AuthenticationSession { time }
        manager.biometricPromptFactory = { _, callback ->
            RecordingPrompt(callback).also { prompts.add(it) }
        }
        manager.init(activity)
        // A previous terminal error is intentionally sticky in the manager.
        // Cancel a fresh prompt to establish its real Loading state for each UI test.
        manager.showAuthenticationPrompt("Fixture reset", "Fixture reset")
        manager.resetAuthentication()
        prompts.clear()
        owner.registry.currentState = Lifecycle.State.CREATED
        recomposer = Recomposer(compositionScope.coroutineContext)
        compositionScope.launch { recomposer.runRecomposeAndApplyChanges() }
        view = ComposeView(activity)
        view.setParentCompositionContext(recomposer)
        activity.setContentView(view)
        navController = NavHostController(activity).apply {
            setLifecycleOwner(activity)
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = "settings") {
                composable("settings") {}
                composable(Screen.WebView.route) {}
            }
        }
    }

    @After
    fun tearDown() {
        view.disposeComposition()
        recomposer.cancel()
        compositionScope.cancel()
        manager.clear(activity)
        manager.session = originalSession
        manager.biometricPromptFactory = originalPromptFactory
        controller.pause().stop().destroy()
    }

    @Test
    fun initialLoadingWaitsForStartAndMountsNoProtectedContent() {
        composeWrapper()
        assertEquals(0, prompts.size)
        assertEquals(0, protectedMounts)

        owner.registry.currentState = Lifecycle.State.STARTED
        pumpFrames()

        assertEquals(1, prompts.size)
        assertEquals(1, prompts.single().authenticateCalls)
        assertEquals("Authentication Required", prompts.single().info.title)
        assertEquals("Please authenticate to access settings", prompts.single().info.description)
        assertFalse(manager.hasValidSession())
        assertEquals(0, protectedMounts)
        assertLoading()
    }

    @Test
    fun successReplacesLoadingWithProtectedContentAndLockRemovesIt() {
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()
        assertLoading()

        prompts.single().succeed()
        pumpFrames()
        assertProtectedContent()
        assertEquals(1, protectedMounts)

        manager.resetAuthentication()
        pumpFrames()

        assertLoading()
        assertEquals(1, protectedDisposals)
        assertEquals(2, prompts.size)
        assertEquals(1, prompts.last().authenticateCalls)
    }

    @Test
    fun anExistingValidSessionRendersWithoutAnotherPrompt() {
        authenticate()
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()

        assertProtectedContent()
        assertEquals(1, prompts.size)
        assertEquals(1, protectedMounts)
    }

    @Test
    fun pendingPromptSurvivesRecompositionAndRepeatedStartsWithoutDuplicates() {
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()

        repeat(4) {
            revision.value++
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.RESUMED
            pumpFrames()
            assertLoading()
        }

        assertEquals(1, prompts.size)
        assertEquals(1, prompts.single().authenticateCalls)
        assertEquals(0, prompts.single().cancelCalls)
        assertEquals(1, owner.registry.observerCount)
    }

    @Test
    fun expiredSuccessDoesNotRenderBeforeStartAndRequestsExactlyOnePromptOnStart() {
        authenticate()
        time += 300_000
        composeWrapper()
        assertLoading()
        assertEquals(1, prompts.size)

        owner.registry.currentState = Lifecycle.State.RESUMED
        pumpFrames()

        assertLoading()
        assertEquals(2, prompts.size)
        assertEquals(1, prompts.last().authenticateCalls)
        assertEquals(0, protectedMounts)
    }

    @Test
    fun expiredSessionAfterStopIsRemovedAndRepromptedOnStart() {
        authenticate()
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()
        assertProtectedContent()

        owner.registry.currentState = Lifecycle.State.CREATED
        time += 300_000
        owner.registry.currentState = Lifecycle.State.RESUMED
        pumpFrames()

        assertLoading()
        assertEquals(2, prompts.size)
        assertEquals(1, protectedDisposals)
    }

    @Test
    @Config(sdk = [28, 29])
    fun expiredSessionAfterPauseIsRemovedAndRepromptedOnResumeWithoutAStartEvent() {
        authenticate()
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()
        assertProtectedContent()

        owner.registry.currentState = Lifecycle.State.STARTED
        time += 300_000
        owner.registry.currentState = Lifecycle.State.RESUMED
        pumpFrames()

        assertLoading()
        assertEquals(2, prompts.size)
        assertEquals(1, protectedDisposals)
    }

    @Test
    fun aValidSessionIsRefreshedOnResumeWithoutRemountingOrAnotherPrompt() {
        authenticate()
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()
        owner.registry.currentState = Lifecycle.State.STARTED
        time += 299_999
        owner.registry.currentState = Lifecycle.State.RESUMED
        pumpFrames()
        assertProtectedContent()

        time++
        revision.value++
        pumpFrames()

        assertProtectedContent()
        assertEquals(1, prompts.size)
        assertEquals(1, protectedMounts)
        assertEquals(0, protectedDisposals)
    }

    @Test
    fun recompositionCannotRenderAnExpiredSuccessOrDuplicateItsReplacementPrompt() {
        authenticate()
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()
        time += 300_000

        repeat(4) {
            revision.value++
            pumpFrames()
            assertLoading()
        }

        assertEquals(2, prompts.size)
        assertEquals(1, protectedDisposals)
    }

    @Test
    fun noDeviceCredentialsCreateAValidSessionAndRenderProtectedContent() {
        setDeviceSecure(false)
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()

        assertEquals(AuthenticationManager.AuthenticationResult.AuthenticationNotSet, manager.promptResults.value)
        assertProtectedContent()
        assertTrue(prompts.isEmpty())
    }

    @Test
    fun anExpiredUnsecuredSessionCannotRenderUntilStartEstablishesANewSession() {
        setDeviceSecure(false)
        manager.showAuthenticationPrompt("Test", "Test")
        time += 300_000
        composeWrapper()
        assertLoading()

        owner.registry.currentState = Lifecycle.State.RESUMED
        pumpFrames()

        assertProtectedContent()
        assertTrue(prompts.isEmpty())
    }

    @Test
    fun anErrorRendersRetryAndCancelAndRecompositionDoesNotRetryIt() {
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()
        prompts.single().callback.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED, "cancelled by user")
        pumpFrames()

        repeat(4) {
            revision.value++
            pumpFrames()
        }
        assertTrue(texts().contains("Error: cancelled by user"))
        assertTrue(texts().containsAll(listOf("Retry", "Cancel")))
        assertNoProtectedContent()
        assertEquals(1, prompts.size)

        click("Retry")
        assertLoading()
        assertEquals(2, prompts.size)
        prompts.last().succeed()
        pumpFrames()
        assertProtectedContent()
    }

    @Test
    fun cancellingAnErrorNavigatesToTheWebviewWithoutGrantingAccess() {
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()
        prompts.single().callback.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED, "cancelled")
        pumpFrames()

        click("Cancel")

        assertEquals(Screen.WebView.route, navController.currentDestination?.route)
        assertNoProtectedContent()
        assertFalse(manager.hasValidSession())
        assertEquals(1, prompts.size)
    }

    @Test
    fun resumingAfterUserCancellationKeepsTheErrorUntilAnExplicitRetry() {
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()
        owner.registry.currentState = Lifecycle.State.STARTED
        prompts.single().callback.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED, "cancelled by user")
        pumpFrames()

        owner.registry.currentState = Lifecycle.State.RESUMED
        pumpFrames()

        assertTrue(texts().contains("Error: cancelled by user"))
        assertNoProtectedContent()
        assertEquals(1, prompts.size)
        click("Retry")
        assertLoading()
        assertEquals(2, prompts.size)
    }

    @Test
    fun disposingTheWrapperRemovesItsLifecycleObserverAndDoesNotReprompt() {
        composeWrapper()
        assertEquals(1, owner.registry.observerCount)

        showWrapper.value = false
        pumpFrames()
        assertEquals(0, owner.registry.observerCount)
        owner.registry.currentState = Lifecycle.State.RESUMED
        pumpFrames()

        assertTrue(prompts.isEmpty())
        assertNoProtectedContent()
    }

    @Test
    fun replacingTheLifecycleOwnerRemovesTheOldObserverAndStartsOnlyOnePrompt() {
        composeWrapper()
        val replacement = WrapperLifecycleOwner().apply {
            registry.currentState = Lifecycle.State.CREATED
        }
        currentOwner.value = replacement
        pumpFrames()

        assertEquals(0, owner.registry.observerCount)
        assertEquals(1, replacement.registry.observerCount)
        owner.registry.currentState = Lifecycle.State.RESUMED
        pumpFrames()
        assertTrue(prompts.isEmpty())

        replacement.registry.currentState = Lifecycle.State.RESUMED
        pumpFrames()
        assertLoading()
        assertEquals(1, prompts.size)
    }

    @Test
    fun aPendingCustomPasswordCannotRenderProtectedContentUntilItSucceeds() {
        UserSettings(activity).customAuthPassword = "secret"
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeWrapper()

        assertLoading()
        assertTrue(manager.showCustomAuth.value)
        repeat(3) {
            revision.value++
            pumpFrames()
        }
        assertLoading()
        manager.customAuthSuccess()
        pumpFrames()

        assertProtectedContent()
        assertFalse(manager.showCustomAuth.value)
        assertTrue(prompts.isEmpty())
    }

    private fun composeWrapper() {
        view.setContent {
            val value = revision.value
            CompositionLocalProvider(LocalLifecycleOwner provides currentOwner.value) {
                if (showWrapper.value) {
                    RequireAuthWrapper(navController) {
                        DisposableEffect(Unit) {
                            protectedMounts++
                            onDispose { protectedDisposals++ }
                        }
                        Text("Protected settings $value")
                    }
                }
            }
        }
        pumpFrames()
    }

    private fun authenticate() {
        manager.showAuthenticationPrompt("Test", "Test")
        prompts.last().succeed()
        assertTrue(manager.hasValidSession())
    }

    private fun setDeviceSecure(secure: Boolean) {
        val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguard).setIsDeviceSecure(secure)
    }

    private fun assertProtectedContent() {
        assertTrue(manager.hasValidSession())
        assertEquals(1, protectedMounts - protectedDisposals)
        assertTrue(texts().any { it.startsWith("Protected settings ") })
        assertFalse(texts().contains("Waiting for authentication..."))
    }

    private fun assertNoProtectedContent() {
        assertEquals(0, protectedMounts - protectedDisposals)
        assertFalse(texts().any { it.startsWith("Protected settings ") })
    }

    private fun assertLoading() {
        assertNoProtectedContent()
        assertTrue(texts().contains("Waiting for authentication..."))
    }

    private fun texts(): List<String> = nodes().mapNotNull { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    }

    private fun click(text: String) {
        val node = nodes().single { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
        }
        val action = node.config.getOrNull(SemanticsActions.OnClick)?.action
        assertTrue("Missing or rejected click action for $text", action?.invoke() == true)
        pumpFrames()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun nodes(): List<SemanticsNode> {
        val root = view.getChildAt(0) as ViewRootForTest
        root.measureAndLayoutForTest()
        fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
        return walk(root.semanticsOwner.rootSemanticsNode)
    }

    private fun pumpFrames() {
        // Drive recomposition and animation explicitly, without an idle wait on
        // the loading spinner's infinite animation or a real frame scheduler.
        repeat(6) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            frameNanos += 16_000_000L
            frameClock.sendFrame(frameNanos)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private class RecordingPrompt(val callback: BiometricPrompt.AuthenticationCallback) :
        AuthenticationManager.AuthenticationPrompt {
        var authenticateCalls = 0
        var cancelCalls = 0
        lateinit var info: BiometricPrompt.PromptInfo

        override fun authenticate(info: BiometricPrompt.PromptInfo, cryptoObject: BiometricPrompt.CryptoObject?) {
            this.info = info
            authenticateCalls++
        }

        override fun cancelAuthentication() {
            cancelCalls++
        }

        fun succeed() {
            callback.onAuthenticationSucceeded(
                ReflectionHelpers.callConstructor(
                    BiometricPrompt.AuthenticationResult::class.java,
                    ReflectionHelpers.ClassParameter.from(BiometricPrompt.CryptoObject::class.java, null),
                    ReflectionHelpers.ClassParameter.from(
                        Int::class.javaPrimitiveType!!,
                        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC
                    )
                )
            )
        }
    }
}

class AuthWrapperTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        super.onCreate(savedInstanceState)
    }
}

private class WrapperLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry
}
