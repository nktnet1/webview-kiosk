package uk.nktnet.webviewkiosk.utils

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 29])
@LooperMode(LooperMode.Mode.PAUSED)
class ImmersiveModeRecoveryTest {
    private class WindowView(context: Context) : View(context) {
        var focused = true

        override fun hasWindowFocus(): Boolean = focused
    }

    // Model the visibility reported to an app, not SystemUI's transient surfaces.
    private class ReportedInsets(private val visibleTypes: Int) :
        WindowInsetsCompat(null) {
        override fun isVisible(typeMask: Int): Boolean =
            visibleTypes and typeMask == typeMask
    }

    private lateinit var view: WindowView
    private lateinit var recovery: ImmersiveModeRecovery
    private var eligible = true
    private var insets: WindowInsetsCompat? = ReportedInsets(0)
    private var calls = 0
    private var onRecover: () -> Unit = {}
    private val looper
        get() = shadowOf(Looper.getMainLooper())

    private val delay
        get() = ImmersiveModeRecovery.REHIDE_DELAY_MILLIS

    @Before
    fun setUp() {
        view = WindowView(RuntimeEnvironment.getApplication())
        recovery =
            ImmersiveModeRecovery(
                windowView = view,
                canRecover = { eligible },
                recover = {
                    calls++
                    onRecover()
                },
                readInsets = { insets },
            )
        recovery.resume()
        looper.idle()
        assertEquals(1, calls)
        calls = 0
    }

    @After fun tearDown() = recovery.pause()

    private fun layout() = view.viewTreeObserver.dispatchOnGlobalLayout()

    private fun visible(types: Int) {
        insets = ReportedInsets(types)
        layout()
    }

    private fun advance(millis: Long = delay) =
        looper.idleFor(millis, TimeUnit.MILLISECONDS)

    @Test
    fun statusBarRevealWithoutFocusOrResumeGetsOneDelayedRecovery() {
        visible(WindowInsetsCompat.Type.statusBars())
        advance(delay - 1)
        assertEquals(0, calls)
        advance(1)
        assertEquals(1, calls)
    }

    @Test
    fun navigationBarAloneAlsoRecoversWithoutFocusChange() {
        visible(WindowInsetsCompat.Type.navigationBars())
        advance()
        assertEquals(1, calls)
    }

    @Test
    fun repeatedLayoutsAndTouchesDoNotPostponeTheDeadline() {
        visible(WindowInsetsCompat.Type.systemBars())
        repeat(5) {
            advance(500)
            layout()
            recovery.onUserInteraction()
        }
        assertEquals(0, calls)
        advance(500)
        assertEquals(1, calls)
        advance(delay * 2)
        assertEquals(1, calls)
    }

    @Test
    fun contentTapCanScheduleRecoveryWithoutALayout() {
        insets = ReportedInsets(WindowInsetsCompat.Type.navigationBars())
        recovery.onUserInteraction()
        advance()
        assertEquals(1, calls)
    }

    @Test
    fun normallyHiddenOrTransientBarsAreNotRepeatedlyHidden() {
        repeat(5) {
            layout()
            recovery.onUserInteraction()
            advance()
        }
        assertEquals(0, calls)
    }

    @Test
    fun absentInsetsDoNotCauseSpeculativeRecovery() {
        insets = null
        layout()
        recovery.onUserInteraction()
        advance()
        assertEquals(0, calls)
    }

    @Test
    fun systemHidesBarsBeforeDeadlineCancelsRecovery() {
        visible(WindowInsetsCompat.Type.systemBars())
        advance(1000)
        visible(0)
        advance()
        assertEquals(0, calls)
    }

    @Test
    fun deadlineRechecksVisibilityEvenWithoutAnotherLayout() {
        visible(WindowInsetsCompat.Type.systemBars())
        insets = ReportedInsets(0)
        advance()
        assertEquals(0, calls)
    }

    @Test
    fun persistentPolicyRefusalDoesNotStartAnEndlessHideLoop() {
        onRecover = { layout() }
        visible(WindowInsetsCompat.Type.systemBars())
        advance()
        repeat(5) {
            layout()
            recovery.onUserInteraction()
            advance()
        }
        assertEquals(1, calls)
    }

    @Test
    fun aNewRevealCanRecoverAfterThePreviousRevealFinished() {
        visible(WindowInsetsCompat.Type.systemBars())
        advance()
        visible(0)
        visible(WindowInsetsCompat.Type.navigationBars())
        advance()
        assertEquals(2, calls)
    }

    @Test
    fun leavingWebViewOrDisablingImmersiveBeforeDeadlineCancelsRecovery() {
        visible(WindowInsetsCompat.Type.systemBars())
        eligible = false
        advance()
        layout()
        recovery.onUserInteraction()
        advance()
        assertEquals(0, calls)
    }

    @Test
    fun losingFocusCancelsDelayedRecoveryAndRegainingItRestoresImmediately() {
        visible(WindowInsetsCompat.Type.systemBars())
        view.focused = false
        recovery.onWindowFocusChanged(false)
        advance()
        assertEquals(0, calls)
        view.focused = true
        recovery.onWindowFocusChanged(true)
        assertEquals(1, calls)
    }

    @Test
    fun delayedRecoveryChecksActualWindowFocusAgain() {
        visible(WindowInsetsCompat.Type.systemBars())
        view.focused = false
        advance()
        assertEquals(0, calls)
    }

    @Test
    fun pauseCancelsCallbacksAndUnregistersTheLayoutObserver() {
        visible(WindowInsetsCompat.Type.systemBars())
        recovery.pause()
        advance()
        layout()
        recovery.onUserInteraction()
        recovery.onWindowFocusChanged(true)
        advance()
        assertEquals(0, calls)
    }

    @Test
    fun resumeWaitsForFocusAndDoesNotNeedASecondResume() {
        recovery.pause()
        view.focused = false
        recovery.resume()
        looper.idle()
        assertEquals(0, calls)
        view.focused = true
        recovery.onWindowFocusChanged(true)
        assertEquals(1, calls)
    }

    @Test
    fun resumeWithExistingFocusReappliesAfterLifecycleCanBecomeResumed() {
        recovery.pause()
        eligible = false
        recovery.resume()
        assertEquals(0, calls)
        eligible = true
        looper.idle()
        assertEquals(1, calls)
    }

    @Test
    fun queuedResumeCannotRunAfterPause() {
        recovery.pause()
        recovery.resume()
        recovery.pause()
        looper.idle()
        assertEquals(0, calls)
    }

    @Test
    fun queuedResumeCannotRunAfterFocusLoss() {
        recovery.pause()
        recovery.resume()
        view.focused = false
        recovery.onWindowFocusChanged(false)
        looper.idle()
        assertEquals(0, calls)
    }

    @Test
    fun duplicateResumeDoesNotDuplicateCallbacksOrListeners() {
        recovery.pause()
        recovery.resume()
        recovery.resume()
        looper.idle()
        assertEquals(1, calls)
        visible(WindowInsetsCompat.Type.statusBars())
        advance()
        assertEquals(2, calls)
    }

    @Test
    fun visibleKeyboardSuppressesVisibilityAndFocusRecovery() {
        visible(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        )
        recovery.onUserInteraction()
        recovery.onWindowFocusChanged(true)
        advance()
        assertEquals(0, calls)
        visible(WindowInsetsCompat.Type.navigationBars())
        advance()
        assertEquals(1, calls)
    }

    @Test
    fun keyboardAppearingBeforeDeadlinePreventsRecovery() {
        visible(WindowInsetsCompat.Type.systemBars())
        insets =
            ReportedInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
        advance()
        assertEquals(0, calls)
    }

    @Test
    fun focusRecoveryHonoursCurrentEligibility() {
        eligible = false
        recovery.onWindowFocusChanged(true)
        assertEquals(0, calls)
        eligible = true
        recovery.onWindowFocusChanged(true)
        assertEquals(1, calls)
    }
}
