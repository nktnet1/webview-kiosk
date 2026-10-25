package uk.nktnet.webviewkiosk.utils.webview

import android.util.Log

/**
 * A damaged or obsolete WebView provider can throw NoClassDefFoundError while
 * AndroidX WebKit probes an optional feature. Losing that feature must not prevent
 * the rest of the WebView from starting or being disposed.
 *
 * Keep the WebViewFeature.isFeatureSupported guard inside [block] so lint can verify
 * that optional WebViewCompat calls are correctly guarded.
 */
internal inline fun withOptionalWebViewFeature(
    feature: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (error: LinkageError) {
        Log.w(
            "WebViewFeature",
            "Optional WebView feature unavailable: $feature",
            error,
        )
    }
}
