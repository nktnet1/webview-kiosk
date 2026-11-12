package uk.nktnet.webviewkiosk.utils.webview

import android.app.Activity
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import org.junit.After
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class MediaStateBridgeLifecycleTest {
    private lateinit var activity: ActivityController<Activity>
    private lateinit var webView: WebView
    private lateinit var controller: BackgroundMediaPlaybackController
    private lateinit var bridge: MediaStateBridge

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup()
        webView = WebView(activity.get())
        activity.get().setContentView(webView)
        controller = BackgroundMediaPlaybackController(webView, { true }, {})
        bridge = field("bridge") as MediaStateBridge
    }

    @After
    fun tearDown() {
        controller.dispose()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        activity.pause().stop().destroy()
    }

    @Test
    fun navigationDiscardsReportsAlreadyQueuedByTheOldDocument() {
        bridge.onPlaybackStateChanged("old-frame", true)
        bridge.onMediaInfoChanged("old-frame", "{\"title\":\"Old video\"}")

        controller.onNavigationStarted()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse((field("frames") as PlayingMediaFrames).isPlaying)
        assertTrue((field("mediaByFrame") as Map<*, *>).isEmpty())
    }

    @Test
    fun navigationStillAcceptsReportsFromTheNewDocument() {
        controller.onNavigationStarted()
        bridge.onPlaybackStateChanged("new-frame", true)
        bridge.onMediaInfoChanged("new-frame", "{\"title\":\"New video\"}")

        shadowOf(Looper.getMainLooper()).idle()

        assertTrue((field("frames") as PlayingMediaFrames).contains("new-frame"))
        assertTrue((field("mediaByFrame") as Map<*, *>).containsKey("new-frame"))
    }

    @Test
    fun notificationStopDiscardsEarlierGesturesAndPlaybackReports() {
        bridge.onUserInteraction()
        bridge.onPlaybackStateChanged("old-frame", true)
        controller.javaClass
            .getDeclaredMethod(
                "stopPlayback",
                Boolean::class.javaPrimitiveType,
            )
            .apply { isAccessible = true }
            .invoke(controller, true)

        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(field("stoppedByUser") as Boolean)
        assertFalse((field("frames") as PlayingMediaFrames).isPlaying)
    }

    @Test
    fun notificationPauseDiscardsEarlierGesturesButAllowsAFreshGesture() {
        controller.javaClass
            .getDeclaredField("serviceRequested")
            .apply { isAccessible = true }
            .setBoolean(controller, true)
        bridge.onUserInteraction()
        bridge.onPlaybackStateChanged("old-frame", true)
        controller.javaClass
            .getDeclaredMethod("pauseFromNotificationInternal")
            .apply { isAccessible = true }
            .invoke(controller)

        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(field("pausedByNotification") as Boolean)
        assertFalse((field("frames") as PlayingMediaFrames).isPlaying)
        bridge.onUserInteraction()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(field("pausedByNotification") as Boolean)
    }

    private fun field(name: String): Any? =
        controller.javaClass
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(controller)
}
