package uk.nktnet.webviewkiosk.utils.webview

import android.webkit.WebView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackgroundMediaPlaybackControllerLifecycleTest {
    @Test
    fun notificationStopRequiresFreshForegroundGestureBeforePageCanPlayAgain() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        val controller = BackgroundMediaPlaybackController(webView, { true }, {})
        val stopped =
            controller.javaClass.getDeclaredField("stoppedByUser").apply {
                isAccessible = true
            }
        try {
            stopped.setBoolean(controller, true)
            controller.onUserInteraction()
            assertFalse(stopped.getBoolean(controller))

            stopped.setBoolean(controller, true)
            controller.onHostBackgroundChanged(true)
            controller.onUserInteraction()
            assertTrue(stopped.getBoolean(controller))
        } finally {
            controller.dispose()
            webView.destroy()
        }
    }

    @Test
    fun rendererDeathDoesNotDeliverDeferredVisibilityToDeadWebView() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        var releaseCalled = false
        val controller =
            BackgroundMediaPlaybackController(webView, { true }) {
                releaseCalled = true
            }
        // Simulate a playback session whose renderer has just died.
        controller.javaClass
            .getDeclaredField("serviceRequested")
            .apply {
                isAccessible = true
            }
            .setBoolean(controller, true)
        try {
            controller.dispose(rendererGone = true)
            assertFalse(releaseCalled)
        } finally {
            webView.destroy()
        }
    }
}
