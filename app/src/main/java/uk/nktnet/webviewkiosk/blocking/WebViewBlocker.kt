package uk.nktnet.webviewkiosk.blocking

import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import java.io.ByteArrayInputStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import uk.nktnet.webviewkiosk.adblock.BlockingResponse
import uk.nktnet.webviewkiosk.config.Constants

/**
 * Native request headers supply resource/initiator facts when available. The current
 * page cannot identify a child frame's initiator; an empty source keeps context-free
 * filtering only. Ambiguous types remain "other". Main frames match themselves.
 */
internal object WebViewBlocker {
    private val empty = ByteArray(0)
    private val resources by lazy {
        BlockingResponse.entries
            .filter { it.resourcePath != null }
            .associateWith { response ->
                lazy {
                    WebViewBlocker::class
                        .java
                        .getResourceAsStream(
                            "/adblock/ubo/${response.resourcePath}"
                        )!!
                        .use { it.readBytes() }
                }
            }
    }
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
        requestHeaders: Map<String, String>,
        method: String,
        service: ContentBlockingService,
    ): Boolean =
        responseFor(
            url,
            isMainFrame,
            requestHeaders,
            method,
            service,
        ) != BlockingResponse.ALLOW

    fun shouldBlockDocument(
        url: String,
        method: String,
        service: ContentBlockingService,
    ): Boolean =
        responseFor(url, true, emptyMap(), method, service) != BlockingResponse.ALLOW

    /** Only known GET navigations can be replayed without losing a request body. */
    fun documentUrl(
        url: String,
        method: String,
        service: ContentBlockingService,
    ): String {
        if (
            '?' !in url ||
                !method.equals("GET", true) ||
                !isFilterableUrl(url) ||
                shouldBlockDocument(url, method, service)
        ) {
            return url
        }
        return service.removeQueryParameters(url, url, "document", method)
    }

    private fun responseFor(
        url: String,
        isMainFrame: Boolean,
        requestHeaders: Map<String, String>,
        method: String,
        service: ContentBlockingService,
    ): BlockingResponse {
        if (!isFilterableUrl(url)) {
            return BlockingResponse.ALLOW
        }
        if (isMainFrame) {
            return service.responseFor(url, url, "document", method)
        }
        val source = initiator(url, requestHeaders)
        if (source.isNotEmpty() && !isFilterableUrl(source)) {
            return BlockingResponse.ALLOW
        }
        return service.responseFor(
            url,
            source,
            resourceType(requestHeaders),
            method,
        )
    }

    internal fun initiator(
        url: String,
        headers: Map<String, String>,
    ): String {
        fun header(name: String) =
            headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.trim()
        // A referrer can survive sandboxing or redirect origin tainting. A present
        // Origin, even an opaque/null one, must not borrow that referrer's context.
        header("origin")?.let {
            return sourceUrl(it)
        }
        header("referer")
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                return sourceUrl(it)
            }
        if (header("sec-fetch-site").equals("same-origin", true)) {
            // This header proves the origin, including its scheme and port. The
            // same-site/cross-site values do not identify an individual origin.
            val target = url.toHttpUrlOrNull() ?: return ""
            return target
                .newBuilder()
                .username("")
                .password("")
                .encodedPath("/")
                .query(null)
                .fragment(null)
                .build()
                .toString()
        }
        // Neither the top frame nor a last-seen request proves which iframe made
        // this request. Opaque origins and absent metadata remain unknown.
        return ""
    }

    private fun sourceUrl(value: String): String {
        if (value.isEmpty() || value.any { it.isWhitespace() || it == '\\' }) {
            return ""
        }
        val source = value.toUri()
        return when {
            // Exempt only proven local origins. Opaque URLs and malformed header
            // values mean unknown context, not an exemption from generic rules.
            source.isHierarchical &&
                (source.scheme.equals("file", true) ||
                    source.scheme.equals("content", true)) -> value
            source.isHierarchical &&
                !source.encodedAuthority.isNullOrEmpty() &&
                value.toHttpUrlOrNull() != null -> value
            else -> ""
        }
    }

    fun intercept(
        request: WebResourceRequest,
        service: ContentBlockingService,
        blockedDocumentHtml: (String) -> String = {
            "<!doctype html><title>Access Blocked</title>" +
                "<h1>Access Blocked</h1><p>URL matches a content blocking filter</p>"
        },
    ): WebResourceResponse? {
        val response =
            responseFor(
                request.url.toString(),
                request.isForMainFrame,
                request.requestHeaders.orEmpty(),
                request.method,
                service,
            )
        if (response == BlockingResponse.ALLOW) {
            return null
        }
        if (request.isForMainFrame) {
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                403,
                "Forbidden",
                mapOf(
                    "Cache-Control" to "no-store",
                    "X-Content-Type-Options" to "nosniff",
                ),
                blockedDocumentHtml(request.url.toString()).byteInputStream(),
            )
        }
        val type =
            response.mimeType
                ?: when (response) {
                    BlockingResponse.EMPTY_SCRIPT -> "application/javascript"
                    BlockingResponse.EMPTY_STYLESHEET -> "text/css"
                    BlockingResponse.EMPTY_HTML -> "text/html"
                    BlockingResponse.EMPTY_TEXT,
                    BlockingResponse.BLOCK -> "text/plain"
                    BlockingResponse.TRANSPARENT_GIF -> "image/gif"
                    BlockingResponse.TRANSPARENT_PNG,
                    BlockingResponse.TRANSPARENT_2X2_PNG -> "image/png"
                    else -> error("Missing bundled response MIME type")
                }
        val bytes =
            if (response.resourcePath != null) {
                resources.getValue(response).value
            } else {
                when (response) {
                    BlockingResponse.TRANSPARENT_GIF -> transparentGif
                    BlockingResponse.TRANSPARENT_PNG -> transparentPng
                    BlockingResponse.TRANSPARENT_2X2_PNG -> transparent2x2Png
                    else -> empty
                }
            }
        val encoding =
            when {
                type.startsWith("image/") ||
                    type.startsWith("audio/") ||
                    type.startsWith("video/") -> null
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
                ?.trim()
                ?.lowercase(java.util.Locale.ROOT)
        return when (destination) {
            "image" -> "image"

            "script",
            "worker",
            "sharedworker",
            "serviceworker" -> "script"

            "object",
            "embed" -> "object"

            "style" -> "stylesheet"

            "font" -> "font"

            "audio",
            "video",
            "track" -> "media"

            "iframe",
            "frame" -> "subdocument"

            else -> {
                if (
                    headers.entries.any {
                        it.key.equals("x-requested-with", true) &&
                            it.value.equals("XMLHttpRequest", true)
                    }
                ) {
                    return "xmlhttprequest"
                }
                // An explicit empty/unknown destination is authoritative. Fetch,
                // XHR and beacons cannot reliably be separated from it alone.
                if (destination != null) {
                    return "other"
                }
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
