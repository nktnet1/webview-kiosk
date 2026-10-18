package uk.nktnet.webviewkiosk.blocking

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Synchronous, boolean-only policy lookup for APIs absent from native interception.
 */
internal class WebViewRequests
private constructor(
    private val webView: WebView,
    internal val rules: Rules,
) {
    private var handler: ScriptHandler? = null

    internal class Rules(private val service: ContentBlockingService) {
        @Volatile internal var disposed = false

        @JavascriptInterface
        fun shouldBlock(url: String?, source: String?, type: String?): Boolean {
            if (
                disposed ||
                    url == null ||
                    source == null ||
                    type == null ||
                    url.length > 8192 ||
                    source.length > 8192 ||
                    !WebViewBlocker.isFilterableUrl(source)
            ) {
                return false
            }
            val target =
                when (type) {
                    "websocket" ->
                        when {
                            url.startsWith("wss://") -> "https://" + url.drop(6)
                            url.startsWith("ws://") -> "http://" + url.drop(5)
                            else -> return false
                        }
                    "ping" -> url
                    else -> return false
                }
            return WebViewBlocker.isFilterableUrl(target) &&
                service.shouldBlock(
                    url,
                    source,
                    type,
                    if (type == "ping") {
                        "POST"
                    } else {
                        "GET"
                    },
                )
        }
    }

    fun dispose() {
        rules.disposed = true
        if (
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            runCatching { handler?.remove() }
        }
        webView.removeJavascriptInterface(BRIDGE)
    }

    companion object {
        private const val BRIDGE = "wkRequestRules"

        fun attach(
            webView: WebView,
            service: ContentBlockingService,
        ): WebViewRequests? {
            var binding: WebViewRequests? = null
            return try {
                if (
                    !WebViewFeature.isFeatureSupported(
                        WebViewFeature.DOCUMENT_START_SCRIPT
                    )
                ) {
                    return null
                }
                binding = WebViewRequests(webView, Rules(service))
                webView.addJavascriptInterface(binding.rules, BRIDGE)
                val script =
                    webView.context.assets
                        .open("content-blocking/requests.js")
                        .bufferedReader()
                        .use { it.readText() }
                binding.handler =
                    WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        script,
                        setOf("*"),
                    )
                binding
            } catch (error: Exception) {
                runCatching { binding?.dispose() }
                Log.w("ContentBlocking", "Request hooks are unavailable", error)
                null
            } catch (error: LinkageError) {
                runCatching { binding?.dispose() }
                Log.w(
                    "ContentBlocking",
                    "Request hook provider is unavailable",
                    error,
                )
                null
            }
        }
    }
}
