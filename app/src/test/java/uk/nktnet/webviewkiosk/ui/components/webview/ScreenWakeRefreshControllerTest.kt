package uk.nktnet.webviewkiosk.ui.components.webview

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.After
import org.junit.Assert.assertEquals
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
@Config(sdk = [28])
@Suppress(
    "DEPRECATION",
    "UsePropertyAccessSyntax",
) // Control power state independently to test event ordering.
class ScreenWakeRefreshControllerTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry

        fun send(event: Lifecycle.Event) = registry.handleLifecycleEvent(event)
    }

    private class WindowView(context: Context) : View(context) {
        var focused = true

        override fun hasWindowFocus(): Boolean = focused

        fun focus(value: Boolean) {
            focused = value
            ReflectionHelpers.callInstanceMethod<Unit>(
                viewTreeObserver,
                "dispatchOnWindowFocusChange",
                ClassParameter.from(Boolean::class.javaPrimitiveType, value),
            )
        }
    }

    private lateinit var context: Context
    private lateinit var power: PowerManager
    private lateinit var owner: Owner
    private lateinit var view: WindowView
    private lateinit var controller: ScreenWakeRefreshController
    private var enabled = true
    private var currentWebView = true
    private var refreshes = 0

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
        view = WindowView(context)
        owner.send(Lifecycle.Event.ON_RESUME)
        controller = newController()
        controller.attach()
    }

    @After fun tearDown() = controller.detach()

    private fun newController() =
        ScreenWakeRefreshController(
            context,
            owner.lifecycle,
            view,
            canRefresh = { enabled && currentWebView },
            refresh = { refreshes++ },
        )

    @Test
    fun initialResumeRotationAndUnpairedScreenOnNeverRefresh() {
        screenOn()
        owner.send(Lifecycle.Event.ON_PAUSE)
        owner.send(Lifecycle.Event.ON_RESUME)
        controller.detach()
        controller = newController()
        controller.attach()
        screenOn()
        assertEquals(0, refreshes)
    }

    @Test
    fun eachSleepWakePairRefreshesOnceDespiteDuplicateBroadcasts() {
        screenOff()
        screenOff()
        screenOn()
        screenOn()
        view.focus(true)
        assertEquals(1, refreshes)
        screenOff()
        screenOn()
        assertEquals(2, refreshes)
    }

    @Test
    fun wakeBeforeResumeWaitsForResumeAndWindowFocus() {
        screenOff()
        view.focus(false)
        owner.send(Lifecycle.Event.ON_PAUSE)
        owner.send(Lifecycle.Event.ON_STOP)
        screenOn()
        assertEquals(0, refreshes)
        owner.send(Lifecycle.Event.ON_RESUME)
        assertEquals(0, refreshes)
        view.focus(true)
        assertEquals(1, refreshes)
    }

    @Test
    fun resumeBeforeWakeWaitsForTheScreenOnBroadcast() {
        screenOff()
        owner.send(Lifecycle.Event.ON_PAUSE)
        owner.send(Lifecycle.Event.ON_RESUME)
        assertEquals(0, refreshes)
        screenOn()
        assertEquals(1, refreshes)
    }

    @Test
    fun aSleepPauseBeforeTheScreenOffBroadcastRetainsEligibility() {
        shadowOf(power).setIsInteractive(false)
        owner.send(Lifecycle.Event.ON_PAUSE)
        owner.send(Lifecycle.Event.ON_STOP)
        screenOff()
        screenOn()
        owner.send(Lifecycle.Event.ON_RESUME)
        assertEquals(1, refreshes)
    }

    @Test
    fun sleepingWhileAnotherAppIsForegroundDoesNotRefreshOnReturn() {
        owner.send(Lifecycle.Event.ON_PAUSE)
        owner.send(Lifecycle.Event.ON_STOP)
        screenOff()
        screenOn()
        owner.send(Lifecycle.Event.ON_RESUME)
        view.focus(true)
        assertEquals(0, refreshes)
    }

    @Test
    fun anotherAppTakingForegroundAfterWakeDiscardsThePendingRefresh() {
        view.focus(false)
        screenOff()
        screenOn()
        owner.send(Lifecycle.Event.ON_PAUSE)
        owner.send(Lifecycle.Event.ON_STOP)
        owner.send(Lifecycle.Event.ON_RESUME)
        view.focus(true)
        assertEquals(0, refreshes)
    }

    @Test
    fun inactiveSettingsOrDisablingDuringSleepCannotQueueARefresh() {
        currentWebView = false
        screenOff()
        screenOn()
        currentWebView = true
        view.focus(true)
        assertEquals(0, refreshes)

        screenOff()
        enabled = false
        screenOn()
        enabled = true
        view.focus(true)
        assertEquals(0, refreshes)
    }

    @Test
    fun disablingOrReplacingTheWebViewBeforeFocusDiscardsThePendingWake() {
        view.focus(false)
        screenOff()
        screenOn()
        enabled = false
        view.focus(true)
        enabled = true
        view.focus(true)
        assertEquals(0, refreshes)

        view.focus(false)
        screenOff()
        screenOn()
        currentWebView = false
        view.focus(true)
        currentWebView = true
        view.focus(true)
        assertEquals(0, refreshes)
    }

    @Test
    fun rapidSleepWakeCyclesCoalesceUntilTheKioskOwnsTheWindow() {
        view.focus(false)
        repeat(4) {
            screenOff()
            screenOn()
        }
        view.focus(true)
        assertEquals(1, refreshes)
        view.focus(false)
        view.focus(true)
        assertEquals(1, refreshes)
    }

    @Test
    fun detachRemovesTheReceiverAndFocusListenerAndLosesPendingWake() {
        val before =
            shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.size
        view.focus(false)
        screenOff()
        screenOn()
        controller.detach()
        controller.detach()
        assertEquals(
            before - 1,
            shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.size,
        )
        view.focus(true)
        screenOff()
        screenOn()
        owner.send(Lifecycle.Event.ON_PAUSE)
        owner.send(Lifecycle.Event.ON_RESUME)
        assertEquals(0, refreshes)

        controller = newController()
        controller.attach()
        controller.attach()
        screenOn()
        assertEquals(0, refreshes)
        screenOff()
        screenOn()
        assertEquals(1, refreshes)
    }

    @Test
    fun destroyingTheOwnerCancelsPendingWork() {
        view.focus(false)
        screenOff()
        screenOn()
        owner.send(Lifecycle.Event.ON_DESTROY)
        view.focus(true)
        screenOff()
        screenOn()
        assertEquals(0, refreshes)
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
