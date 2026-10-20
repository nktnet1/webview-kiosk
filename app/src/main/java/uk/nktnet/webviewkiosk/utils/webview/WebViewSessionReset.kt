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
    for (action in actions) {
        when (action) {
            ResetActionOption.HISTORY -> {
                systemSettings.clearHistory()
                webView.clearHistory()
            }
            ResetActionOption.COOKIES -> {
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
            ResetActionOption.WEB_STORAGE -> {
                WebStorage.getInstance().deleteAllData()
            }
            ResetActionOption.CACHE -> webView.clearCache(true)
            ResetActionOption.FORM_DATA -> {
                webView.clearFormData()
                @Suppress("DEPRECATION")
                WebViewDatabase.getInstance(webView.context).clearFormData()
            }
            ResetActionOption.SSL_PREFERENCES -> webView.clearSslPreferences()
            // The host recreates the WebView after all cleanup and before loading
            // home.
            ResetActionOption.RECREATE_WEBVIEW -> Unit
        }
    }
}
