package uk.nktnet.webviewkiosk.utils.webview

import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * JavaScript reports arrive on a WebView background thread. Dispatch them to the
 * main thread before touching the WebView, media session, or service.
 *
 * Keep this class and its annotated signature in scripts/r8/r8_audit.py: R8 must not
 * rename methods invoked by WebView JavaScript.
 */
internal class MediaStateBridge(
    private val webView: WebView,
    private val report: (String, Boolean) -> Unit,
) {
    @JavascriptInterface
    fun onPlaybackStateChanged(frameId: String, playing: Boolean) {
        webView.post { report(frameId, playing) }
    }
}
