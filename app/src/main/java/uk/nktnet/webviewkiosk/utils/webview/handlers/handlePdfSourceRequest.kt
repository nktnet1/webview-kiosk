package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.ClientCertRequest
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.isLocalFileLink
import uk.nktnet.webviewkiosk.utils.resolveLocalFileLink
import uk.nktnet.webviewkiosk.utils.webview.HttpAuthRequest
import uk.nktnet.webviewkiosk.utils.webview.PdfTlsState
import uk.nktnet.webviewkiosk.utils.webview.SslErrorRequest
import uk.nktnet.webviewkiosk.utils.webview.SchemeType
import uk.nktnet.webviewkiosk.utils.webview.getBlockInfo
import uk.nktnet.webviewkiosk.utils.webview.getWebViewRequestCookie
import uk.nktnet.webviewkiosk.utils.webview.isPdfViewerUrl
import uk.nktnet.webviewkiosk.utils.webview.storeWebViewResponseCookies
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

private const val PDF_SOURCE_TTL_MS = 24 * 60 * 60 * 1000L
private const val MAX_REDIRECTS = 5
private const val MAX_REGISTERED_PDF_SOURCES = 64

private class RegisteredPdfSource(
    val url: String,
    @Volatile var expiresAt: Long,
    val credentials: ConcurrentHashMap<PdfAuthScope, String> = ConcurrentHashMap(),
    val tls: PdfTlsState = PdfTlsState(),
) {
    val pendingAuthentication = ConcurrentHashMap<PdfAuthScope, HttpAuthRequest>()
}

private data class PdfAuthScope(val origin: String, val realm: String)

private data class BasicChallenge(val realm: String, val utf8: Boolean)

private val registeredPdfSources =
    ConcurrentHashMap<String, RegisteredPdfSource>()

fun registerPdfSource(sourceUrl: String, previousToken: String? = null): String {
    cleanupExpiredPdfSources()
    val previousSource = previousToken?.let(::resolvePdfSource)?.takeIf { it.url == sourceUrl }
    if (registeredPdfSources.size >= MAX_REGISTERED_PDF_SOURCES) {
        registeredPdfSources.entries
            .minByOrNull { it.value.expiresAt }
            ?.let { removePdfSource(it.key, it.value) }
    }
    val token = UUID.randomUUID().toString()
    registeredPdfSources[token] = RegisteredPdfSource(
        url = sourceUrl,
        expiresAt = System.currentTimeMillis() + PDF_SOURCE_TTL_MS,
        // Refresh credentials within this viewer, while giving every document its own event token.
        credentials = ConcurrentHashMap(previousSource?.credentials.orEmpty()),
        tls = PdfTlsState(previousSource?.tls),
    )
    previousSource?.tls?.cancelPendingRequests()
    return token
}

fun cancelPdfSourceRequests(token: String?) {
    token?.let { registeredPdfSources[it]?.tls?.cancelPendingRequests() }
}

fun isPdfSourceNavigation(token: String?, navigationUrl: String?): Boolean {
    val source = token?.let { registeredPdfSources[it] } ?: return false
    if (navigationUrl == source.url) return true
    val uri = navigationUrl?.toUri() ?: return false
    return isPdfViewerUrl(uri) && uri.getQueryParameter("wk_pdf_token") == token
        && uri.getQueryParameter("wk_pdf_url") == source.url
}

fun handlePdfSourceRequest(
    context: Context,
    request: WebResourceRequest,
    userAgent: String?,
    userSettings: UserSettings,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>,
    onAuthenticationRequired: (token: String, url: String, request: HttpAuthRequest) -> Unit =
        { _, _, request -> request.cancel() },
    onAuthenticated: (token: String, url: String) -> Unit = { _, _ -> },
    onViewerEvent: (token: String, url: String, event: String) -> Unit = { _, _, _ -> },
    onClientCertificateRequired: (token: String, url: String, request: ClientCertRequest) -> Unit =
        { _, _, request -> request.ignore() },
    onSslError: (token: String, url: String, request: SslErrorRequest) -> Unit =
        { _, _, request -> request.cancel() },
): WebResourceResponse? {
    val requestUrl = request.url
    val expectedOrigin = Constants.PDF_JS_ASSETS_DUMMY_URL.toUri()

    if (
        requestUrl.scheme != expectedOrigin.scheme
        || requestUrl.host != expectedOrigin.host
        || requestUrl.port !in listOf(-1, 443)
        || requestUrl.userInfo != null
        || requestUrl.path !in listOf("/pdf_source", "/pdf_status")
    ) {
        return null
    }

    val sourceToken = requestUrl.getQueryParameter("wk_pdf_token")
        ?: return errorResponse(400, "Missing PDF source token")
    val source = resolvePdfSource(sourceToken)

    return try {
        if (requestUrl.path == "/pdf_status") {
            val event = requestUrl.getQueryParameter("event")
            if (event !in listOf("loaded", "error", "authentication", "retry")) {
                return errorResponse(400, "Invalid PDF viewer event")
            }
            // The active viewer can still retry after its source registration expires.
            onViewerEvent(sourceToken, source?.url.orEmpty(), event!!)
            return errorResponse(200, "OK")
        }
        val sourceUrl = source?.url ?: return errorResponse(404, "PDF source expired")
        val (schemeType, blockCause) = getBlockInfo(
            url = sourceUrl,
            blacklistRegexes = blacklistRegexes,
            whitelistRegexes = whitelistRegexes,
            userSettings = userSettings
        )
        if (blockCause != null) {
            return errorResponse(403, "PDF source is blocked")
        }

        val sourceUri = sourceUrl.toUri()
        when {
            sourceUri.isLocalFileLink() -> localPdfResponse(
                sourceUri.resolveLocalFileLink(context)
            )
            schemeType == SchemeType.FILE -> localPdfResponse(
                sourceUri.path?.let(::File)
            )
            schemeType == SchemeType.WEB -> remotePdfResponse(
                context = context,
                sourceUrl = sourceUrl,
                request = request,
                userAgent = userAgent,
                userSettings = userSettings,
                blacklistRegexes = blacklistRegexes,
                whitelistRegexes = whitelistRegexes,
                sourceToken = sourceToken,
                source = source,
                onAuthenticationRequired = onAuthenticationRequired,
                onAuthenticated = onAuthenticated,
                onClientCertificateRequired = onClientCertificateRequired,
                onSslError = onSslError,
            )
            else -> errorResponse(400, "Unsupported PDF URL")
        }
    } catch (e: PdfSourceBlockedException) {
        Log.w(Constants.APP_SCHEME, "Blocked PDF source request: ${e.message}")
        errorResponse(403, "PDF source is blocked")
    } catch (e: Exception) {
        if (source?.tls?.hasPendingRequests == true) {
            return errorResponse(401, "Waiting for TLS authentication")
        }
        Log.e(Constants.APP_SCHEME, "Failed to load PDF source: ${source?.url}", e)
        errorResponse(502, "Failed to load PDF")
    }
}

private fun localPdfResponse(file: File?): WebResourceResponse {
    if (file == null || !file.isFile) {
        return errorResponse(404, "PDF not found")
    }

    return WebResourceResponse(
        "application/pdf",
        null,
        FileInputStream(file)
    )
}

private fun remotePdfResponse(
    context: Context,
    sourceUrl: String,
    request: WebResourceRequest,
    userAgent: String?,
    userSettings: UserSettings,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>,
    sourceToken: String,
    source: RegisteredPdfSource,
    onAuthenticationRequired: (String, String, HttpAuthRequest) -> Unit,
    onAuthenticated: (String, String) -> Unit,
    onClientCertificateRequired: (String, String, ClientCertRequest) -> Unit,
    onSslError: (String, String, SslErrorRequest) -> Unit,
): WebResourceResponse {
    var currentUrl = sourceUrl
    var redirectCount = 0
    var attemptedScope: PdfAuthScope? = null
    var authorization: String? = null

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
        var responseOwnsConnection = false
        try {
            if (connection is HttpsURLConnection) {
                source.tls.configureConnection(
                    connection, context, userSettings,
                    isActive = {
                        registeredPdfSources[sourceToken] === source
                            && source.expiresAt > System.currentTimeMillis()
                    },
                    onClientCertificateRequired = { onClientCertificateRequired(sourceToken, source.url, it) },
                    onSslError = { onSslError(sourceToken, source.url, it) },
                    onApproved = { onAuthenticated(sourceToken, source.url) },
                )
            }
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000

            userAgent?.takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("User-Agent", it)
            }
            getWebViewRequestCookie(currentUrl)
                ?.let { connection.setRequestProperty("Cookie", it) }
            authorization?.let { connection.setRequestProperty("Authorization", it) }

            listOf("Range", "If-Range", "Accept").forEach { header ->
                request.requestHeaders[header]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { connection.setRequestProperty(header, it) }
            }

            val statusCode = connection.responseCode
            storeWebViewResponseCookies(currentUrl, connection.headerFields)
            if (statusCode in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")

                if (location.isNullOrBlank()) {
                    return errorResponse(502, "Invalid PDF redirect")
                }
                if (redirectCount++ >= MAX_REDIRECTS) {
                    return errorResponse(508, "Too many PDF redirects")
                }

                currentUrl = URL(URL(currentUrl), location).toString()
                // Challenge each redirect target independently, including changes of scheme or port.
                authorization = null
                attemptedScope = null
                continue
            }

            if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                val challenge = connection.headerFields.entries
                    .filter { it.key.equals("WWW-Authenticate", ignoreCase = true) }
                    .flatMap { it.value.orEmpty() }
                    .firstNotNullOfOrNull(::parseBasicChallenge)
                if (challenge != null) {
                    val target = URL(currentUrl)
                    val port = target.port.takeIf { it >= 0 } ?: target.defaultPort
                    val authScope = PdfAuthScope(
                        "${target.protocol.lowercase()}://${target.host.lowercase()}:$port",
                        challenge.realm
                    )
                    val savedAuthorization = source.credentials[authScope]
                    if (authorization == null && savedAuthorization != null) {
                        attemptedScope = authScope
                        authorization = savedAuthorization
                        continue
                    }
                    if (authorization != null && attemptedScope == authScope) {
                        source.credentials.remove(authScope, authorization)
                    }
                    requestPdfAuthentication(
                        sourceToken, source, authScope, challenge, target.host,
                        onAuthenticationRequired, onAuthenticated
                    )
                }
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
                if (name != null && values != null && !name.equals("Set-Cookie", ignoreCase = true)) {
                    responseHeaders[name] = values.joinToString(", ")
                }
            }
            responseHeaders["Cache-Control"] = "no-store"

            val response = WebResourceResponse(
                connection.contentType?.substringBefore(';') ?: "application/pdf",
                null,
                statusCode,
                responseMessage,
                responseHeaders,
                DisconnectingInputStream(input, connection)
            )
            responseOwnsConnection = true
            return response
        } finally {
            if (!responseOwnsConnection) connection.disconnect()
        }
    }
}

private fun requestPdfAuthentication(
    token: String,
    source: RegisteredPdfSource,
    authScope: PdfAuthScope,
    challenge: BasicChallenge,
    host: String,
    onAuthenticationRequired: (String, String, HttpAuthRequest) -> Unit,
    onAuthenticated: (String, String) -> Unit
) {
    lateinit var request: HttpAuthRequest
    request = HttpAuthRequest(
        host = host,
        realm = challenge.realm,
        onSubmit = { username, password ->
            source.pendingAuthentication.remove(authScope, request)
            if (registeredPdfSources[token] === source && source.expiresAt > System.currentTimeMillis()) {
                val charset = if (challenge.utf8) Charsets.UTF_8 else Charsets.ISO_8859_1
                source.credentials[authScope] = "Basic " + Base64.encodeToString(
                    "$username:$password".toByteArray(charset), Base64.NO_WRAP
                )
                onAuthenticated(token, source.url)
            }
        },
        onCancel = { source.pendingAuthentication.remove(authScope, request) }
    )
    if (source.pendingAuthentication.putIfAbsent(authScope, request) == null) {
        onAuthenticationRequired(token, source.url, request)
    }
}

private fun parseBasicChallenge(header: String): BasicChallenge? {
    // Split only outside quoted strings; realms can contain commas or escaped quotes.
    val parts = mutableListOf<String>()
    var start = 0
    var quoted = false
    var escaped = false
    header.forEachIndexed { index, char ->
        when {
            escaped -> escaped = false
            quoted && char == '\\' -> escaped = true
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                parts.add(header.substring(start, index).trim())
                start = index + 1
            }
        }
    }
    parts.add(header.substring(start).trim())
    val scheme = Regex("^([A-Za-z][A-Za-z0-9_-]*)(?:\\s+(?!\\s*=)|$)")
    val basicIndex = parts.indexOfFirst {
        scheme.find(it)?.groupValues?.get(1).equals("Basic", ignoreCase = true)
    }
    if (basicIndex < 0) return null
    val parameters = listOf(parts[basicIndex].substring(5)) + parts.drop(basicIndex + 1)
        .takeWhile { scheme.find(it) == null }
    val challenge = parameters.joinToString(",")
    val realmMatch = Regex(
        """(?:^|,)\s*realm\s*=\s*(?:"((?:\\.|[^"\\])*)"|([^,\s]+))""",
        RegexOption.IGNORE_CASE
    ).find(challenge) ?: return null
    val realm = realmMatch.groups[1]?.value?.replace(Regex("""\\(.)"""), "$1")
        ?: realmMatch.groupValues[2]
    val utf8 = Regex(
        """(?:^|,)\s*charset\s*=\s*(?:"UTF-8"|UTF-8)(?:\s*,|\s*$)""",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(challenge)
    return BasicChallenge(realm, utf8)
}

private fun resolvePdfSource(token: String): RegisteredPdfSource? {
    val now = System.currentTimeMillis()
    val entry = registeredPdfSources[token] ?: return null

    if (entry.expiresAt <= now) {
        removePdfSource(token, entry)
        return null
    }

    entry.expiresAt = now + PDF_SOURCE_TTL_MS
    return entry
}

private fun removePdfSource(token: String, source: RegisteredPdfSource) {
    if (registeredPdfSources.remove(token, source)) {
        source.pendingAuthentication.values.forEach { it.cancel() }
        source.tls.cancelPendingRequests()
    }
}

private fun cleanupExpiredPdfSources() {
    val now = System.currentTimeMillis()
    registeredPdfSources.entries.forEach { entry ->
        if (entry.value.expiresAt <= now) {
            removePdfSource(entry.key, entry.value)
        }
    }
}

private fun errorResponse(statusCode: Int, message: String): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "UTF-8",
        statusCode,
        message,
        mapOf("Cache-Control" to "no-store"),
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
