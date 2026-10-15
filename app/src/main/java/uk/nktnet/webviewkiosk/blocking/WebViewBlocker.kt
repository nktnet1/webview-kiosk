package uk.nktnet.webviewkiosk.blocking

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.config.Constants
import java.io.ByteArrayInputStream

/**
 * WebView has no reliable resource-type or initiator-frame API. Only match subresources with a
 * known top-level URL; unknown types are "other" and can underblock typed rules.
 */
internal object WebViewBlocker {
    private val empty = ByteArray(0)

    fun shouldBlock(
        url: String,
        isMainFrame: Boolean,
        pageUrl: String,
        requestHeaders: Map<String, String>,
        method: String,
        service: ContentBlockingService,
    ): Boolean {
        if (isMainFrame || !isFilterableUrl(url) || !isFilterableUrl(pageUrl)) return false
        return service.shouldBlock(url, pageUrl, resourceType(requestHeaders), method)
    }

    fun intercept(
        request: WebResourceRequest,
        pageUrl: String,
        service: ContentBlockingService,
    ): WebResourceResponse? {
        if (
            !shouldBlock(
                request.url.toString(),
                request.isForMainFrame,
                pageUrl,
                request.requestHeaders.orEmpty(),
                request.method,
                service,
            )
        ) {
            return null
        }
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(empty))
    }

    internal fun isFilterableUrl(url: String): Boolean {
        val uri = url.toUri()
        if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) return false
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return false
        // Compare hosts, including origins without a trailing slash, before matching.
        return !host.equals("appassets.androidplatform.net", true) &&
            !host.equals(Constants.PDF_JS_ASSETS_DUMMY_URL.toUri().host, true)
    }

    internal fun resourceType(headers: Map<String, String>): String {
        val destination =
            headers.entries
                .firstOrNull {
                    it.key.equals("sec-fetch-dest", ignoreCase = true)
                }
                ?.value
                ?.lowercase()
        return when (destination) {
            "image" -> "image"

            "script" -> "script"

            "style" -> "stylesheet"

            "font" -> "font"

            "audio",
            "video",
            "track",
            -> "media"

            "iframe",
            "frame",
            -> "subdocument"

            else -> {
                val accept =
                    headers.entries
                        .firstOrNull {
                            it.key.equals("accept", ignoreCase = true)
                        }
                        ?.value
                        ?.lowercase()
                        .orEmpty()
                when {
                    accept.startsWith("image/") -> "image"
                    accept.startsWith("text/css") -> "stylesheet"
                    else -> "other"
                }
            }
        }
    }
}
