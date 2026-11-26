package uk.nktnet.webviewkiosk.ui.components.webview

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
@Suppress("DEPRECATION", "UsePropertyAccessSyntax")
class ForegroundWebViewLifecycleControllerTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry

        fun send(event: Lifecycle.Event) = registry.handleLifecycleEvent(event)
    }

    private class WindowView(context: Context) : View(context) {
        fun focus(hasFocus: Boolean = true) {
            ReflectionHelpers.callInstanceMethod<Unit>(
                viewTreeObserver,
                "dispatchOnWindowFocusChange",
                ClassParameter.from(Boolean::class.javaPrimitiveType, hasFocus),
            )
        }
    }

    private lateinit var context: Context
    private lateinit var power: PowerManager
    private lateinit var owner: Owner
    private lateinit var view: WindowView
    private lateinit var controller: ForegroundWebViewLifecycleController
    private var pauses = 0
    private var resumes = 0

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(
                "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
            )
        power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(power).setIsInteractive(true)
        owner = Owner()
        owner.send(Lifecycle.Event.ON_RESUME)
        view = WindowView(context)
        controller = newController()
        controller.attach()
        pauses = 0
        resumes = 0
    }

    @After fun tearDown() = controller.detach()

    private fun newController(context: Context = this.context) =
        ForegroundWebViewLifecycleController(
            context,
            owner.lifecycle,
            view,
            pause = { pauses++ },
            resume = { resumes++ },
        )

    @Test
    fun missingPowerServiceLeavesDirectGpsUnavailable() {
        controller.detach()
        controller =
            newController(
                object : ContextWrapper(context) {
                    override fun getSystemService(name: String): Any? =
                        if (name == POWER_SERVICE) {
                            null
                        } else {
                            super.getSystemService(name)
                        }
                }
            )
        assertFalse(controller.attach())
        screenOff()
        screenOn()
        assertEquals(0, pauses)
        assertEquals(0, resumes)
    }

    @Test
    fun attachingWhileStartedResumesWebViewEvenIfPreviouslyPaused() {
        controller.detach()
        controller = newController()
        controller.attach()
        assertEquals(0, pauses)
        assertEquals(1, resumes)
    }

    @Test
    fun screenSleepAndWakePauseAndResumeWithoutActivityLifecycleEvents() {
        screenOff()
        screenOff()
        assertEquals(1, pauses)
        assertEquals(0, resumes)
        screenOn()
        screenOn()
        assertEquals(1, pauses)
        assertEquals(1, resumes)
        screenOff()
        screenOn()
        assertEquals(2, pauses)
        assertEquals(2, resumes)
    }

    @Test
    fun delayedScreenOffAfterWakeDoesNotPauseTheWebViewAgain() {
        screenOff()
        shadowOf(power).setIsInteractive(true)
        view.focus()
        assertEquals(1, resumes)

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, pauses)
        assertEquals(1, resumes)
    }

    @Test
    fun delayedScreenOnWhileAsleepDoesNotResumeTheWebView() {
        screenOff()
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, pauses)
        assertEquals(0, resumes)

        shadowOf(power).setIsInteractive(true)
        view.focus()
        assertEquals(1, resumes)
    }

    @Test
    fun screenSleepViaActivityPauseWithoutStopCanRecoverOnResume() {
        shadowOf(power).setIsInteractive(false)
        owner.send(Lifecycle.Event.ON_PAUSE)
        assertEquals(1, pauses)
        assertEquals(0, resumes)
        shadowOf(power).setIsInteractive(true)
        owner.send(Lifecycle.Event.ON_RESUME)
        assertEquals(1, pauses)
        assertEquals(1, resumes)
    }

    @Test
    fun activityResumeWhileStillAsleepMustNotResumeWebView() {
        screenOff()
        owner.send(Lifecycle.Event.ON_PAUSE)
        owner.send(Lifecycle.Event.ON_RESUME)
        assertEquals(1, pauses)
        assertEquals(0, resumes)
        screenOn()
        assertEquals(1, resumes)
    }

    @Test
    fun activityStartWhileAsleepWithoutScreenOffDoesNotResumeEarly() {
        owner.send(Lifecycle.Event.ON_STOP)
        shadowOf(power).setIsInteractive(false)
        owner.send(Lifecycle.Event.ON_START)
        owner.send(Lifecycle.Event.ON_RESUME)
        assertEquals(1, pauses)
        assertEquals(0, resumes)

        shadowOf(power).setIsInteractive(true)
        view.focus()
        assertEquals(1, resumes)
    }

    @Test
    fun activityResumeDetectsSleepAfterAnInteractivePauseWithoutScreenOff() {
        owner.send(Lifecycle.Event.ON_PAUSE)
        assertEquals(0, pauses)
        shadowOf(power).setIsInteractive(false)
        owner.send(Lifecycle.Event.ON_RESUME)
        assertEquals(1, pauses)
        assertEquals(0, resumes)

        shadowOf(power).setIsInteractive(true)
        view.focus()
        assertEquals(1, resumes)
    }

    @Test
    fun focusGainWhileAsleepDetectsMissingScreenOff() {
        shadowOf(power).setIsInteractive(false)
        view.focus()
        assertEquals(1, pauses)
        assertEquals(0, resumes)

        view.focus(false)
        assertEquals(1, pauses)
        shadowOf(power).setIsInteractive(true)
        view.focus()
        assertEquals(1, resumes)
    }

    @Test
    fun reattachResamplesLifecycleChangesWhileDetached() {
        controller.detach()
        owner.send(Lifecycle.Event.ON_STOP)
        controller.attach()
        assertEquals(1, pauses)
        assertEquals(0, resumes)

        owner.send(Lifecycle.Event.ON_START)
        owner.send(Lifecycle.Event.ON_RESUME)
        assertEquals(1, resumes)
    }

    @Test
    fun conventionalActivityStopsAndStartsStillControlWebView() {
        owner.send(Lifecycle.Event.ON_PAUSE)
        assertEquals(0, pauses)
        owner.send(Lifecycle.Event.ON_STOP)
        assertEquals(1, pauses)
        owner.send(Lifecycle.Event.ON_START)
        assertEquals(1, resumes)
        owner.send(Lifecycle.Event.ON_RESUME)
        assertEquals(1, resumes)
    }

    @Test
    fun stopThenSleepDoesNotDoublePauseOrResumeBeforeWake() {
        owner.send(Lifecycle.Event.ON_STOP)
        screenOff()
        owner.send(Lifecycle.Event.ON_START)
        assertEquals(1, pauses)
        assertEquals(0, resumes)
        screenOn()
        assertEquals(1, resumes)
    }

    @Test
    fun wakingBeforeActivityStartDefersResumeUntilStarted() {
        screenOff()
        owner.send(Lifecycle.Event.ON_STOP)
        screenOn()
        assertEquals(0, resumes)
        owner.send(Lifecycle.Event.ON_START)
        assertEquals(1, resumes)
    }

    @Test
    fun screenOffAfterLifecycleStopAndScreenOnBeforeStartIsSafe() {
        owner.send(Lifecycle.Event.ON_STOP)
        screenOff()
        screenOn()
        assertEquals(1, pauses)
        assertEquals(0, resumes)
        owner.send(Lifecycle.Event.ON_START)
        assertEquals(1, resumes)
    }

    @Test
    fun returnToForegroundRecoversWhenVendorOmitsScreenOn() {
        screenOff()
        shadowOf(power).setIsInteractive(true)
        view.focus()
        assertEquals(1, resumes)
        screenOn()
        assertEquals(1, resumes)
    }

    @Test
    fun focusChangeCanRecoverWhenBothScreenBroadcastsAreMissing() {
        shadowOf(power).setIsInteractive(false)
        view.focus(false)
        assertEquals(1, pauses)
        shadowOf(power).setIsInteractive(true)
        view.focus()
        assertEquals(1, resumes)
    }

    @Test
    fun ordinaryWindowFocusLossDoesNotPauseTheWebView() {
        view.focus(false)
        view.focus()
        assertEquals(0, pauses)
        assertEquals(0, resumes)
    }

    @Test
    fun activityStartCanRecoverMissingScreenOn() {
        screenOff()
        owner.send(Lifecycle.Event.ON_STOP)
        shadowOf(power).setIsInteractive(true)
        owner.send(Lifecycle.Event.ON_START)
        assertEquals(1, resumes)
    }

    @Test
    fun attachingWhileDisplayOffStartsPausedAndDoesNotResumeEarly() {
        controller.detach()
        shadowOf(power).setIsInteractive(false)
        controller = newController()
        controller.attach()
        assertEquals(1, pauses)
        screenOn()
        assertEquals(1, resumes)
    }

    @Test
    fun detachAndDestroyStopListening() {
        val before =
            shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.size
        screenOff()
        controller.detach()
        controller.detach()
        assertEquals(
            before - 1,
            shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.size,
        )
        screenOn()
        view.focus()
        owner.send(Lifecycle.Event.ON_STOP)
        assertEquals(0, resumes)

        controller = newController()
        controller.attach()
        owner.send(Lifecycle.Event.ON_DESTROY)
        val pausesBefore = pauses
        val resumesBefore = resumes
        screenOff()
        screenOn()
        view.focus()
        assertEquals(pausesBefore, pauses)
        assertEquals(resumesBefore, resumes)
    }

    private fun screenOff() {
        shadowOf(power).setIsInteractive(false)
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun screenOn() {
        shadowOf(power).setIsInteractive(true)
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
    }
}
