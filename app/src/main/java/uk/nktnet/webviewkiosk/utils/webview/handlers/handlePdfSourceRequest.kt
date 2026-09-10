package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.config.Constants
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

fun handlePdfSourceRequest(
    request: WebResourceRequest,
    userAgent: String?,
    allowLocalFiles: Boolean
): WebResourceResponse? {
    val requestUrl = request.url
    val expectedHost = Constants.PDF_JS_ASSETS_DUMMY_URL.toUri().host

    if (requestUrl.host != expectedHost || requestUrl.path != "/pdf_source") {
        return null
    }

    val sourceUrl = requestUrl.getQueryParameter("wk_pdf_url")
        ?: return errorResponse(400, "Missing PDF URL")
    val sourceUri = sourceUrl.toUri()

    return try {
        when (sourceUri.scheme?.lowercase()) {
            "file" -> {
                if (!allowLocalFiles) {
                    return errorResponse(403, "Local file access is disabled")
                }

                val path = sourceUri.path
                    ?: return errorResponse(400, "Invalid file URL")
                val file = File(path)

                if (!file.isFile) {
                    return errorResponse(404, "PDF not found")
                }

                WebResourceResponse(
                    "application/pdf",
                    null,
                    FileInputStream(file)
                )
            }
            "http", "https" -> remotePdfResponse(sourceUrl, userAgent)
            else -> errorResponse(400, "Unsupported PDF URL")
        }
    } catch (e: Exception) {
        Log.e(Constants.APP_SCHEME, "Failed to load PDF source: $sourceUrl", e)
        errorResponse(502, "Failed to load PDF")
    }
}

private fun remotePdfResponse(
    sourceUrl: String,
    userAgent: String?
): WebResourceResponse {
    val connection = URL(sourceUrl).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000

    userAgent?.takeIf { it.isNotBlank() }?.let {
        connection.setRequestProperty("User-Agent", it)
    }
    CookieManager.getInstance().getCookie(sourceUrl)
        ?.takeIf { it.isNotBlank() }
        ?.let { connection.setRequestProperty("Cookie", it) }

    val statusCode = connection.responseCode
    val responseMessage = connection.responseMessage
        ?.takeIf { it.isNotBlank() }
        ?: if (statusCode in 200..299) "OK" else "Error"
    val input = if (statusCode in 200..299) {
        connection.inputStream
    } else {
        connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
    }
    return WebResourceResponse(
        connection.contentType?.substringBefore(';') ?: "application/pdf",
        null,
        statusCode,
        responseMessage,
        emptyMap(),
        DisconnectingInputStream(input, connection)
    )
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
