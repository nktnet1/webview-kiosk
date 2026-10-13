package uk.nktnet.webviewkiosk.ui.components.webview

import android.app.Activity
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class KeepScreenOnWindowControllerTest {
    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
        fun send(event: Lifecycle.Event) = registry.handleLifecycleEvent(event)
    }

    private fun Activity.isKeepScreenOn(): Boolean =
        (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0

    @Test
    fun flagOnlyAppliesWhileResumedAndIsRestoredAfterReturningToForeground() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val owner = TestOwner()
        val controller = KeepScreenOnWindowController(activity.window, owner.lifecycle, true)
        controller.attach()
        assertFalse(activity.isKeepScreenOn())

        owner.send(Lifecycle.Event.ON_CREATE)
        owner.send(Lifecycle.Event.ON_START)
        assertFalse("STARTED is not visible enough for this flag", activity.isKeepScreenOn())
        owner.send(Lifecycle.Event.ON_RESUME)
        assertTrue(activity.isKeepScreenOn())

        owner.send(Lifecycle.Event.ON_PAUSE)
        assertFalse(activity.isKeepScreenOn())
        owner.send(Lifecycle.Event.ON_RESUME)
        assertTrue(activity.isKeepScreenOn())

        controller.detach()
        assertFalse(activity.isKeepScreenOn())
        owner.send(Lifecycle.Event.ON_PAUSE)
        owner.send(Lifecycle.Event.ON_RESUME)
        assertFalse("the detached observer must not re-enable the flag", activity.isKeepScreenOn())
    }

    @Test
    fun recompositionWhilePausedDoesNotReenableScreenFlag() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val owner = TestOwner()
        owner.send(Lifecycle.Event.ON_CREATE)
        owner.send(Lifecycle.Event.ON_START)
        owner.send(Lifecycle.Event.ON_RESUME)
        owner.send(Lifecycle.Event.ON_PAUSE)

        val controller = KeepScreenOnWindowController(activity.window, owner.lifecycle, true)
        controller.attach()
        assertFalse(activity.isKeepScreenOn())
        owner.send(Lifecycle.Event.ON_RESUME)
        assertTrue(activity.isKeepScreenOn())
        controller.detach()
    }

    @Test
    fun disablingTheOptionClearsAnExistingWindowFlag() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val owner = TestOwner()
        owner.send(Lifecycle.Event.ON_CREATE)
        owner.send(Lifecycle.Event.ON_START)
        owner.send(Lifecycle.Event.ON_RESUME)

        val enabled = KeepScreenOnWindowController(activity.window, owner.lifecycle, true)
        enabled.attach()
        assertTrue(activity.isKeepScreenOn())
        enabled.detach()
        val disabled = KeepScreenOnWindowController(activity.window, owner.lifecycle, false)
        disabled.attach()
        assertFalse(activity.isKeepScreenOn())
        disabled.detach()
    }
}
