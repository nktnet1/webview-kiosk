package uk.nktnet.webviewkiosk.utils.webview

import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import uk.nktnet.webviewkiosk.config.Constants

/**
 * Owns the final release of one WebView. Both Compose disposal and renderer death
 * can try to release the same instance, but WebView.destroy() is not repeatable.
 */
internal class WebViewLifetime {
    private var released = false

    fun release(
        webView: WebView,
        rendererGone: Boolean,
        disposeClients: () -> Unit,
    ): Boolean {
        if (released) {
            return false
        }
        released = true

        try {
            disposeClients()
        } finally {
            // A dead renderer must not receive any more WebView commands.
            if (!rendererGone) {
                runCatching { webView.stopLoading() }
            }
            // Android requires that a WebView is detached before destroy().
            (webView.parent as? ViewGroup)?.removeView(webView)
            if (!rendererGone) {
                runCatching { webView.removeAllViews() }
            }
            runCatching { webView.destroy() }
                .onFailure {
                    Log.w(Constants.APP_SCHEME, "Failed to destroy WebView", it)
                }
        }
        return true
    }
}
