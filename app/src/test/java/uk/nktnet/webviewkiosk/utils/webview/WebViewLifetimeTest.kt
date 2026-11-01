package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.webkit.WebView
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebViewLifetimeTest {
    private class CountingWebView(context: Context) : WebView(context) {
        var stops = 0
        var destroys = 0

        override fun stopLoading() {
            stops++
            super.stopLoading()
        }

        override fun destroy() {
            // Android WebView requires removal from the hierarchy first.
            assertNull(parent)
            destroys++
            super.destroy()
        }
    }

    @Test
    fun normalDisposalDetachesAndDestroysOnlyOnce() {
        val context = RuntimeEnvironment.getApplication()
        val view = CountingWebView(context)
        FrameLayout(context).addView(view)
        val lifetime = WebViewLifetime()
        var clientDisposals = 0

        assertTrue(
            lifetime.release(view, rendererGone = false) { clientDisposals++ }
        )
        assertFalse(
            lifetime.release(view, rendererGone = false) { clientDisposals++ }
        )
        assertEquals(1, clientDisposals)
        assertEquals(1, view.stops)
        assertEquals(1, view.destroys)
        assertNull(view.parent)
    }

    @Test
    fun clientCleanupFailureStillDetachesAndDestroys() {
        val context = RuntimeEnvironment.getApplication()
        val view = CountingWebView(context)
        FrameLayout(context).addView(view)
        val lifetime = WebViewLifetime()

        try {
            lifetime.release(view, rendererGone = false) {
                throw IllegalStateException("client cleanup failed")
            }
            throw AssertionError("Expected cleanup failure")
        } catch (error: IllegalStateException) {
            assertEquals("client cleanup failed", error.message)
        }
        assertNull(view.parent)
        assertEquals(1, view.destroys)
        assertFalse(lifetime.release(view, rendererGone = true) {})
    }

    @Test
    fun rendererDeathSkipsCommandsToDeadRendererEvenWhenComposeDisposesLater() {
        val context = RuntimeEnvironment.getApplication()
        val view = CountingWebView(context)
        FrameLayout(context).addView(view)
        val lifetime = WebViewLifetime()
        var clientDisposals = 0

        assertTrue(lifetime.release(view, rendererGone = true) { clientDisposals++ })
        assertFalse(
            lifetime.release(view, rendererGone = false) { clientDisposals++ }
        )
        assertEquals(1, clientDisposals)
        assertEquals(0, view.stops)
        assertEquals(1, view.destroys)
        assertNull(view.parent)
    }
}
