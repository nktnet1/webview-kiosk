package uk.nktnet.webviewkiosk.utils.webview

import android.app.Dialog
import android.content.ContextWrapper
import android.os.Looper
import androidx.activity.ComponentActivity
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
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.LEGACY)
class WebViewDialogControllerTest {
    private lateinit var activityController: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var controller: WebViewDialogController

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = activityController.get()
        controller = WebViewDialogController(ContextWrapper(ContextWrapper(activity)))
    }

    @After
    fun tearDown() {
        controller.dispose()
        if (!activity.isDestroyed) {
            activityController.pause().stop().destroy()
        }
    }

    @Test
    fun replacingDialogWithSameKeyResolvesPreviousRequestExactlyOnce() {
        val callbacks = mutableListOf<String>()
        val first = Dialog(activity)
        val second = Dialog(activity)

        assertSame(first, controller.show(first, key = "location") { callbacks.add("first:$it") })
        assertSame(second, controller.show(second, key = "location") { callbacks.add("second:$it") })
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("first:true"), callbacks)
        assertFalse(first.isShowing)
        assertTrue(second.isShowing)

        second.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        controller.dismiss("location")

        assertEquals(listOf("first:true", "second:true"), callbacks)
    }

    @Test
    fun webViewCancellationDismissesUiWithoutResolvingNativeRequest() {
        val callbacks = mutableListOf<Boolean>()
        val dialog = Dialog(activity)
        controller.show(dialog, key = "http-auth", onDismiss = callbacks::add)

        controller.dismiss("http-auth", resolveRequest = false)
        shadowOf(Looper.getMainLooper()).idle()
        controller.dispose()

        assertFalse(dialog.isShowing)
        assertEquals(listOf(false), callbacks)
    }

    @Test
    fun activityDestructionDisposesAllPendingDialogsOnlyOnce() {
        val callbacks = mutableListOf<Boolean>()
        val first = Dialog(activity)
        val second = Dialog(activity)
        controller.show(first, key = "permission", onDismiss = callbacks::add)
        controller.show(second, key = "ssl", onDismiss = callbacks::add)

        activityController.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        controller.dispose()

        assertFalse(controller.isActive())
        assertFalse(first.isShowing)
        assertFalse(second.isShowing)
        assertEquals(listOf(true, true), callbacks)
    }

    @Test
    fun disposedControllerCannotShowDialogButCompletesRequest() {
        val callbacks = mutableListOf<Boolean>()
        controller.dispose()
        val dialog = Dialog(activity)

        assertNull(controller.show(dialog, onDismiss = callbacks::add))
        assertFalse(dialog.isShowing)
        assertEquals(listOf(true), callbacks)
    }
}
