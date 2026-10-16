package uk.nktnet.webviewkiosk.blocking

import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import java.io.ByteArrayInputStream
import uk.nktnet.webviewkiosk.adblock.BlockingResponse
import uk.nktnet.webviewkiosk.config.Constants

/**
 * WebView has no reliable resource-type or initiator-frame API. Only match
 * subresources with a known top-level URL; unknown types are "other" and can
 * underblock typed rules.
 */
internal object WebViewBlocker {
    private val empty = ByteArray(0)
    // Small verified transparent placeholders; allocated once, not per request.
    private val transparentGif =
        Base64.decode(
            "R0lGODlhAQABAIEAAAAAAAAAAAAAAAAAACH5BAEAAAAALAAAAAABAAEAAAgEAAEEBAA7",
            Base64.DEFAULT,
        )
    private val transparentPng =
        Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ" +
                "AAAADUlEQVR4nGNgYGBgAAAABQABpfZFQAAAAABJRU5ErkJggg==",
            Base64.DEFAULT,
        )
    private val transparent2x2Png =
        Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0k" +
                "AAAAC0lEQVR4nGNgQAcAABIAAXfx+gAAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )

    fun shouldBlock(
        url: String,
        isMainFrame: Boolean,
        pageUrl: String,
        requestHeaders: Map<String, String>,
        method: String,
        service: ContentBlockingService,
    ): Boolean =
        responseFor(
            url,
            isMainFrame,
            pageUrl,
            requestHeaders,
            method,
            service,
        ) != BlockingResponse.ALLOW

    private fun responseFor(
        url: String,
        isMainFrame: Boolean,
        pageUrl: String,
        requestHeaders: Map<String, String>,
        method: String,
        service: ContentBlockingService,
    ): BlockingResponse {
        if (isMainFrame || !isFilterableUrl(url) || !isFilterableUrl(pageUrl)) {
            return BlockingResponse.ALLOW
        }
        return service.responseFor(
            url,
            pageUrl,
            resourceType(requestHeaders),
            method,
        )
    }

    fun intercept(
        request: WebResourceRequest,
        pageUrl: String,
        service: ContentBlockingService,
    ): WebResourceResponse? {
        val response =
            responseFor(
                request.url.toString(),
                request.isForMainFrame,
                pageUrl,
                request.requestHeaders.orEmpty(),
                request.method,
                service,
            )
        val type =
            when (response) {
                BlockingResponse.ALLOW -> return null
                BlockingResponse.EMPTY_SCRIPT -> "application/javascript"
                BlockingResponse.EMPTY_STYLESHEET -> "text/css"
                BlockingResponse.EMPTY_HTML -> "text/html"
                BlockingResponse.EMPTY_TEXT,
                BlockingResponse.BLOCK -> "text/plain"
                BlockingResponse.TRANSPARENT_GIF -> "image/gif"
                BlockingResponse.TRANSPARENT_PNG,
                BlockingResponse.TRANSPARENT_2X2_PNG -> "image/png"
            }
        val bytes =
            when (response) {
                BlockingResponse.TRANSPARENT_GIF -> transparentGif
                BlockingResponse.TRANSPARENT_PNG -> transparentPng
                BlockingResponse.TRANSPARENT_2X2_PNG -> transparent2x2Png
                else -> empty
            }
        val encoding =
            when {
                type.startsWith("image/") -> null
                else -> "UTF-8"
            }
        return WebResourceResponse(type, encoding, ByteArrayInputStream(bytes))
    }

    internal fun isFilterableUrl(url: String): Boolean {
        val uri = url.toUri()
        if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) {
            return false
        }
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return false
        // Compare hosts, including origins without a trailing slash, before
        // matching.
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
            "track" -> "media"

            "iframe",
            "frame" -> "subdocument"

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
