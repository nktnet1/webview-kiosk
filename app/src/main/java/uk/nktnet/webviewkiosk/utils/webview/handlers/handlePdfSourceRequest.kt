package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.webview.SchemeType
import uk.nktnet.webviewkiosk.utils.webview.getBlockInfo
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val PDF_SOURCE_TTL_MS = 24 * 60 * 60 * 1000L
private const val MAX_REDIRECTS = 5
private const val MAX_REGISTERED_PDF_SOURCES = 64

private data class RegisteredPdfSource(
    val url: String,
    val expiresAt: Long
)

private val registeredPdfSources =
    ConcurrentHashMap<String, RegisteredPdfSource>()

fun registerPdfSource(sourceUrl: String): String {
    cleanupExpiredPdfSources()
    if (registeredPdfSources.size >= MAX_REGISTERED_PDF_SOURCES) {
        registeredPdfSources.entries
            .minByOrNull { it.value.expiresAt }
            ?.let { registeredPdfSources.remove(it.key, it.value) }
    }
    val token = UUID.randomUUID().toString()
    registeredPdfSources[token] = RegisteredPdfSource(
        url = sourceUrl,
        expiresAt = System.currentTimeMillis() + PDF_SOURCE_TTL_MS
    )
    return token
}

fun handlePdfSourceRequest(
    request: WebResourceRequest,
    userAgent: String?,
    userSettings: UserSettings,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>
): WebResourceResponse? {
    val requestUrl = request.url
    val expectedHost = Constants.PDF_JS_ASSETS_DUMMY_URL.toUri().host

    if (requestUrl.host != expectedHost || requestUrl.path != "/pdf_source") {
        return null
    }

    val sourceToken = requestUrl.getQueryParameter("wk_pdf_token")
        ?: return errorResponse(400, "Missing PDF source token")
    val sourceUrl = resolvePdfSource(sourceToken)
        ?: return errorResponse(404, "PDF source expired")

    return try {
        val (schemeType, blockCause) = getBlockInfo(
            url = sourceUrl,
            blacklistRegexes = blacklistRegexes,
            whitelistRegexes = whitelistRegexes,
            userSettings = userSettings
        )
        if (blockCause != null) {
            return errorResponse(403, "PDF source is blocked")
        }

        when (schemeType) {
            SchemeType.FILE -> localPdfResponse(sourceUrl)
            SchemeType.WEB -> remotePdfResponse(
                sourceUrl = sourceUrl,
                request = request,
                userAgent = userAgent,
                userSettings = userSettings,
                blacklistRegexes = blacklistRegexes,
                whitelistRegexes = whitelistRegexes
            )
            else -> errorResponse(400, "Unsupported PDF URL")
        }
    } catch (e: PdfSourceBlockedException) {
        Log.w(Constants.APP_SCHEME, "Blocked PDF source request: ${e.message}")
        errorResponse(403, "PDF source is blocked")
    } catch (e: Exception) {
        Log.e(Constants.APP_SCHEME, "Failed to load PDF source: $sourceUrl", e)
        errorResponse(502, "Failed to load PDF")
    }
}

private fun localPdfResponse(sourceUrl: String): WebResourceResponse {
    val sourceUri = sourceUrl.toUri()
    val path = sourceUri.path
        ?: return errorResponse(400, "Invalid file URL")
    val file = File(path)

    if (!file.isFile) {
        return errorResponse(404, "PDF not found")
    }

    return WebResourceResponse(
        "application/pdf",
        null,
        FileInputStream(file)
    )
}

private fun remotePdfResponse(
    sourceUrl: String,
    request: WebResourceRequest,
    userAgent: String?,
    userSettings: UserSettings,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>
): WebResourceResponse {
    var currentUrl = sourceUrl
    var redirectCount = 0

    while (true) {
        val (schemeType, blockCause) = getBlockInfo(
            url = currentUrl,
            blacklistRegexes = blacklistRegexes,
            whitelistRegexes = whitelistRegexes,
            userSettings = userSettings
        )
        if (schemeType != SchemeType.WEB || blockCause != null) {
            throw PdfSourceBlockedException("Blocked redirect target: $currentUrl")
        }

        val connection = URL(currentUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        userAgent?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("User-Agent", it)
        }
        CookieManager.getInstance().getCookie(currentUrl)
            ?.takeIf { it.isNotBlank() }
            ?.let { connection.setRequestProperty("Cookie", it) }

        listOf("Range", "If-Range", "Accept").forEach { header ->
            request.requestHeaders[header]
                ?.takeIf { it.isNotBlank() }
                ?.let { connection.setRequestProperty(header, it) }
        }

        val statusCode = connection.responseCode
        if (statusCode in setOf(301, 302, 303, 307, 308)) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()

            if (location.isNullOrBlank()) {
                return errorResponse(502, "Invalid PDF redirect")
            }
            if (redirectCount++ >= MAX_REDIRECTS) {
                return errorResponse(508, "Too many PDF redirects")
            }

            currentUrl = URL(URL(currentUrl), location).toString()
            continue
        }

        val responseMessage = connection.responseMessage
            ?.takeIf { it.isNotBlank() }
            ?: if (statusCode in 200..299) "OK" else "Error"
        val input = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
        }
        val responseHeaders = mutableMapOf<String, String>()
        connection.headerFields.forEach { (name, values) ->
            if (name != null && values != null) {
                responseHeaders[name] = values.joinToString(", ")
            }
        }

        return WebResourceResponse(
            connection.contentType?.substringBefore(';') ?: "application/pdf",
            null,
            statusCode,
            responseMessage,
            responseHeaders,
            DisconnectingInputStream(input, connection)
        )
    }
}

private fun resolvePdfSource(token: String): String? {
    val now = System.currentTimeMillis()
    val entry = registeredPdfSources[token] ?: return null

    if (entry.expiresAt <= now) {
        registeredPdfSources.remove(token, entry)
        return null
    }

    registeredPdfSources[token] = entry.copy(
        expiresAt = now + PDF_SOURCE_TTL_MS
    )
    return entry.url
}

private fun cleanupExpiredPdfSources() {
    val now = System.currentTimeMillis()
    registeredPdfSources.entries.forEach { entry ->
        if (entry.value.expiresAt <= now) {
            registeredPdfSources.remove(entry.key, entry.value)
        }
    }
}

private fun errorResponse(statusCode: Int, message: String): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "UTF-8",
        statusCode,
        message,
        emptyMap(),
        ByteArrayInputStream(message.toByteArray(Charsets.UTF_8))
    )
}

private class PdfSourceBlockedException(message: String) : Exception(message)

private class DisconnectingInputStream(
    input: InputStream,
    private val connection: HttpURLConnection
) : FilterInputStream(input) {
    override fun close() {
        try {
            super.close()
        } finally {
            connection.disconnect()
        }
    }
}
