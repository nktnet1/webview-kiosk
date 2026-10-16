package uk.nktnet.webviewkiosk.blocking

import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A data-only bridge: pages can request CSS hide selectors, never call privileged
 * app APIs.
 */
internal class WebViewCosmetics
private constructor(
    private val webView: WebView,
    private val service: ContentBlockingService,
    parentScope: CoroutineScope,
) {
    private val scope =
        CoroutineScope(
            parentScope.coroutineContext +
                SupervisorJob(parentScope.coroutineContext[Job])
        )
    private val pending = AtomicInteger()
    private var handler: ScriptHandler? = null
    private var bridgeInstalled = false
    private var disposed = false
    private val script =
        webView.context.assets
            .open("content-blocking/cosmetic.js")
            .bufferedReader()
            .use {
                it.readText()
            }

    private fun install() {
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        ) {
            return
        }
        WebViewCompat.addWebMessageListener(webView, BRIDGE, setOf("*")) {
            _,
            message,
            origin,
            _,
            reply ->
            val query =
                runCatching { parseQuery(message.data, origin) }.getOrNull()
                    ?: return@addWebMessageListener
            if (disposed) {
                return@addWebMessageListener
            }
            if (pending.incrementAndGet() > MAX_PENDING) {
                pending.decrementAndGet()
                reply.postMessage(
                    JSONObject().put("id", query.id).put("retry", true).toString()
                )
                return@addWebMessageListener
            }
            scope.launch(Dispatchers.Default) {
                try {
                    val selectors =
                        service.cosmeticSelectors(
                            query.url,
                            query.classes,
                            query.ids,
                        )
                    val response =
                        JSONObject()
                            .put("id", query.id)
                            .put("active", service.hasActiveCosmeticRules)
                            .put("selectors", JSONArray(selectors))
                            .toString()
                    withContext(Dispatchers.Main) {
                        if (!disposed) {
                            reply.postMessage(response)
                        }
                    }
                } catch (error: RuntimeException) {
                    Log.w(
                        "ContentBlocking",
                        "Unable to apply cosmetic filters",
                        error,
                    )
                    withContext(Dispatchers.Main) {
                        if (!disposed) {
                            reply.postMessage(
                                JSONObject()
                                    .put("id", query.id)
                                    .put("active", false)
                                    .toString()
                            )
                        }
                    }
                } finally {
                    pending.decrementAndGet()
                }
            }
        }
        bridgeInstalled = true
        if (
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            handler =
                WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }
    }

    /**
     * Also covers older providers and lists enabled after this WebView was created.
     */
    fun onPageFinished() {
        if (
            !disposed &&
                bridgeInstalled &&
                WebViewBlocker.isFilterableUrl(webView.url.orEmpty())
        ) {
            webView.evaluateJavascript(script, null)
        }
    }

    fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        scope.cancel()
        if (
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            runCatching { handler?.remove() }
        }
        if (
            bridgeInstalled &&
                WebViewFeature.isFeatureSupported(
                    WebViewFeature.WEB_MESSAGE_LISTENER
                )
        ) {
            runCatching { WebViewCompat.removeWebMessageListener(webView, BRIDGE) }
        }
    }

    internal data class Query(
        val id: Int,
        val url: String,
        val classes: List<String>,
        val ids: List<String>,
    )

    companion object {
        private const val BRIDGE = "wkCosmeticRules"
        private const val MAX_PENDING = 8

        fun attach(
            webView: WebView,
            service: ContentBlockingService,
            scope: CoroutineScope,
        ): WebViewCosmetics? {
            if (!service.hasActiveCosmeticRules) {
                return null
            }
            var binding: WebViewCosmetics? = null
            return try {
                binding = WebViewCosmetics(webView, service, scope)
                binding.install()
                binding
            } catch (error: Exception) {
                binding?.dispose()
                Log.w("ContentBlocking", "Cosmetic filtering is unavailable", error)
                null
            } catch (error: LinkageError) {
                binding?.dispose()
                Log.w(
                    "ContentBlocking",
                    "Cosmetic filtering provider is unavailable",
                    error,
                )
                null
            }
        }

        internal fun parseQuery(data: String?, origin: Uri): Query? {
            // Allow JSON escaping of two batches of 256 x 128-character tokens.
            if (
                data == null ||
                    data.length > 256 * 1024 ||
                    !WebViewBlocker.isFilterableUrl("$origin/")
            ) {
                return null
            }
            return runCatching {
                val value = JSONObject(data)
                val id = value.getInt("id")
                val url = value.getString("url")
                if (
                    id <= 0 ||
                        url.length > 8192 ||
                        !WebViewBlocker.isFilterableUrl(url)
                ) {
                    return null
                }
                val page = Uri.parse(url)
                // sourceOrigin comes from WebView, not the untrusted message body.
                if (
                    !page.scheme.equals(origin.scheme, true) ||
                        !page.host.equals(origin.host, true) ||
                        effectivePort(page) != effectivePort(origin)
                ) {
                    return null
                }
                Query(
                    id,
                    url,
                    tokens(value.getJSONArray("classes")),
                    tokens(value.getJSONArray("ids")),
                )
            }
                .getOrNull()
        }

        private fun effectivePort(uri: Uri): Int =
            if (uri.port >= 0) {
                uri.port
            } else if (uri.scheme.equals("https", true)) {
                443
            } else {
                80
            }

        private fun tokens(array: JSONArray): List<String> {
            require(array.length() <= 256)
            return (0 until array.length())
                .map { index ->
                    (array.get(index) as? String ?: error("Expected a token string"))
                        .also {
                            require(it.length <= 128 && it.none(Char::isISOControl))
                        }
                }
                .distinct()
        }
    }
}
