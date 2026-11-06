package uk.nktnet.webviewkiosk.utils.webview

import android.app.Activity
import android.os.Looper
import android.webkit.WebView
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class NfcBridgeManagerTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var webView: WebView

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        webView = WebView(controller.get())
        controller.get().setContentView(webView)
        NfcBridgeManager.attachWebView(webView)
    }

    @After
    fun tearDown() {
        NfcBridgeManager.detachWebView(webView)
        controller.pause().stop().destroy()
    }

    @Test
    fun rearmDoesNotStartScanningWhenPageHasNoActiveReader() {
        NfcBridgeManager.rearmActiveReaders()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun rearmRequestsExistingReadersOnly() {
        NfcBridgeManager.setScanActive(true)
        NfcBridgeManager.rearmActiveReaders()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(webView).lastEvaluatedJavascript.contains("rearmScan"))
    }

    @Test
    fun resettingPageSessionPreventsQueuedRearm() {
        NfcBridgeManager.setScanActive(true)
        NfcBridgeManager.rearmActiveReaders()
        NfcBridgeManager.resetSession()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun stoppingScanPreventsQueuedRearm() {
        NfcBridgeManager.setScanActive(true)
        NfcBridgeManager.rearmActiveReaders()
        NfcBridgeManager.setScanActive(false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun newPageCanStartScanningAfterNavigationReset() {
        NfcBridgeManager.setScanActive(true)
        NfcBridgeManager.resetSession() // onPageStarted
        NfcBridgeManager.rearmActiveReaders()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(webView).lastEvaluatedJavascript)

        NfcBridgeManager.setScanActive(true) // new document's scan()
        NfcBridgeManager.rearmActiveReaders()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(webView).lastEvaluatedJavascript.contains("rearmScan"))
    }

    @Test
    fun staleClientCannotStopOrRestartNewPageScan() {
        val oldClient = NfcBridgeManager.openClient()
        assertTrue(NfcBridgeManager.startClientScan(oldClient))
        NfcBridgeManager.resetSession() // old document navigates away

        val newClient = NfcBridgeManager.openClient()
        assertTrue(NfcBridgeManager.startClientScan(newClient))
        assertFalse(NfcBridgeManager.startClientScan(oldClient))
        NfcBridgeManager.stopClientScan(oldClient)
        NfcBridgeManager.releaseClient(oldClient)

        NfcBridgeManager.rearmActiveReaders()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(webView).lastEvaluatedJavascript.contains("rearmScan"))
    }

    @Test
    fun stoppingOneDocumentPreservesOtherDocumentsScan() {
        val first = NfcBridgeManager.openClient()
        val second = NfcBridgeManager.openClient()
        assertTrue(NfcBridgeManager.startClientScan(first))
        assertTrue(NfcBridgeManager.startClientScan(second))
        NfcBridgeManager.stopClientScan(first)
        NfcBridgeManager.releaseClient(first)

        NfcBridgeManager.rearmActiveReaders()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(webView).lastEvaluatedJavascript.contains("rearmScan"))

        NfcBridgeManager.stopClientScan(second)
        assertFalse(NfcBridgeManager.isCurrentClient(first))
        assertTrue(NfcBridgeManager.isCurrentClient(second))
    }

    @Test
    fun releasedFrameCannotLeavePendingWriteForNextTag() {
        val outgoing = NfcBridgeManager.openClient()
        val current = NfcBridgeManager.openClient()
        assertNotNull(NfcBridgeManager.queueWrite("{}", outgoing))

        // A navigation in one frame must cancel its queued write, while
        // allowing the still-active frame to queue its own request.
        NfcBridgeManager.releaseClient(outgoing)
        assertNull(NfcBridgeManager.queueWrite("{}", outgoing))
        assertNotNull(NfcBridgeManager.queueWrite("{}", current))
    }

    @Test
    fun stalePageCannotQueueWriteForNewPage() {
        val oldClient = NfcBridgeManager.openClient()
        NfcBridgeManager.resetSession()
        val newClient = NfcBridgeManager.openClient()

        assertNull(NfcBridgeManager.queueWrite("{}", oldClient))
        assertNotNull(NfcBridgeManager.queueWrite("{}", newClient))
    }
}
