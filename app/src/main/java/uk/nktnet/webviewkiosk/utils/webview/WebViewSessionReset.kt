package uk.nktnet.webviewkiosk.utils.webview

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.option.ResetActionOption

/** Clear selected browsing data before the inactivity reset navigates home. */
suspend fun clearSelectedResetData(
    webView: WebView,
    systemSettings: SystemSettings,
    actions: Collection<ResetActionOption>,
) {
    if (ResetActionOption.HISTORY in actions) {
        systemSettings.clearHistory()
        webView.clearHistory()
    }
    if (ResetActionOption.COOKIES in actions) {
        val cookieManager = CookieManager.getInstance()
        // Deletion is asynchronous; do not load home before its completion.
        suspendCancellableCoroutine { continuation ->
            cookieManager.removeAllCookies {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
        cookieManager.flush()
    }
    if (ResetActionOption.WEB_STORAGE in actions) {
        WebStorage.getInstance().deleteAllData()
    }
    if (ResetActionOption.CACHE in actions) {
        webView.clearCache(true)
    }
    if (ResetActionOption.FORM_DATA in actions) {
        webView.clearFormData()
        @Suppress("DEPRECATION")
        WebViewDatabase.getInstance(webView.context).clearFormData()
    }
    if (ResetActionOption.SSL_PREFERENCES in actions) {
        webView.clearSslPreferences()
    }
    // RECREATE_WEBVIEW is handled by the WebviewScreen host after cleanup.
}
