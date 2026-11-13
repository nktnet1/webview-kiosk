package uk.nktnet.webviewkiosk.utils.webview

import android.net.Uri
import android.webkit.WebView
import androidx.core.net.toUri
import java.net.URLEncoder
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.isLocalFileLink
import uk.nktnet.webviewkiosk.utils.webview.html.BlockCause
import uk.nktnet.webviewkiosk.utils.webview.html.generateBlockedPageHtml

const val BLOCK_HOST = "block"
private const val MAX_WRAPPED_URL_DEPTH = 10
internal const val MAX_NAVIGATION_URL_LENGTH = 16_384

enum class SchemeType {
    FILE,
    WEB,
    DATA,
    APP_CUSTOM,
    OTHER,
}

fun isBlockedUrl(
    url: String,
    blacklistRegexes: List<String>,
    whitelistRegexes: List<String>,
): Boolean = urlRegexBlockCause(url, blacklistRegexes, whitelistRegexes) != null

internal fun urlRegexBlockCause(
    url: String,
    blacklistRegexes: List<String>,
    whitelistRegexes: List<String>,
    match: ((String, String) -> UrlRegexMatchResult)? = null,
): BlockCause? {
    val evaluate =
        match
            ?: run {
                val rules =
                    UrlRegexMatcher.forRules(blacklistRegexes, whitelistRegexes)
                val matcher: (String, String) -> UrlRegexMatchResult =
                    { source, target ->
                        rules.match(source, target)
                    }
                matcher
            }
    val blacklistCause =
        blacklistRegexes.firstNotNullOfOrNull { regex ->
            when (evaluate(regex, url)) {
                UrlRegexMatchResult.MATCH -> BlockCause.BLACKLIST
                UrlRegexMatchResult.ERROR -> BlockCause.URL_REGEX_ERROR
                UrlRegexMatchResult.NO_MATCH -> null
            }
        } ?: return null

    var cause = blacklistCause
    for (regex in whitelistRegexes) {
        when (evaluate(regex, url)) {
            UrlRegexMatchResult.MATCH -> return null
            UrlRegexMatchResult.NO_MATCH -> Unit
            UrlRegexMatchResult.ERROR -> {
                if (cause == BlockCause.BLACKLIST) {
                    cause = BlockCause.URL_REGEX_ERROR
                }
            }
        }
    }
    return cause
}

private fun hasInvalidPercentEscape(url: String): Boolean =
    url.indices.any { index ->
        url[index] == '%' &&
            (index + 2 >= url.length ||
                !url[index + 1].isHexDigit() ||
                !url[index + 2].isHexDigit())
    }

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun isMalformedNavigationUrl(url: String, uri: Uri): Boolean {
    if (url.any { it < ' ' || it == '\u007f' }) {
        return true
    }
    val isWeb =
        uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)
    if (isWeb && (uri.host.isNullOrBlank() || ' ' in url)) {
        return true
    }
    val isBlockPage =
        uri.scheme.equals(Constants.APP_SCHEME, ignoreCase = true) &&
            uri.host == BLOCK_HOST
    return (isWeb || isBlockPage) && hasInvalidPercentEscape(url)
}

internal fun isInvalidNavigationUrl(url: String): Boolean {
    if (url.length > MAX_NAVIGATION_URL_LENGTH) {
        return true
    }
    return runCatching { isMalformedNavigationUrl(url, url.toUri()) }
        .getOrDefault(true)
}

fun getBlockInfo(
    url: String,
    blacklistRegexes: List<String>,
    whitelistRegexes: List<String>,
    userSettings: UserSettings,
): Pair<SchemeType, BlockCause?> {
    if (url.length > MAX_NAVIGATION_URL_LENGTH) {
        return SchemeType.OTHER to BlockCause.INVALID_URL
    }
    val uri =
        runCatching { url.toUri() }.getOrNull()
            ?: return SchemeType.OTHER to BlockCause.INVALID_URL
    val scheme = uri.scheme?.lowercase() ?: ""
    val schemeType =
        when (scheme) {
            "file" -> SchemeType.FILE
            "http",
            "https" -> SchemeType.WEB
            "data" -> SchemeType.DATA
            Constants.APP_SCHEME -> SchemeType.APP_CUSTOM
            else -> SchemeType.OTHER
        }

    if (runCatching { isMalformedNavigationUrl(url, uri) }.getOrDefault(true)) {
        return schemeType to BlockCause.INVALID_URL
    }
    val regexBlockCause = urlRegexBlockCause(url, blacklistRegexes, whitelistRegexes)
    val blockCause =
        when {
            regexBlockCause != null -> regexBlockCause
            (schemeType == SchemeType.FILE || uri.isLocalFileLink()) &&
                !userSettings.allowLocalFiles -> BlockCause.LOCAL_FILE
            else -> null
        }
    return schemeType to blockCause
}

internal data class ResolvedBlockPageTarget(
    val url: String,
    val schemeType: SchemeType,
    val blockCause: BlockCause?,
)

fun resolveBlockPageUrl(
    url: String,
    blacklistRegexes: List<String>,
    whitelistRegexes: List<String>,
    userSettings: UserSettings,
): String? =
    resolveBlockPageTarget(url, blacklistRegexes, whitelistRegexes, userSettings)
        ?.url

internal fun resolveBlockPageTarget(
    url: String,
    blacklistRegexes: List<String>,
    whitelistRegexes: List<String>,
    userSettings: UserSettings,
): ResolvedBlockPageTarget? {
    var targetUrl = url
    var depth = 0
    while (true) {
        if (isInvalidNavigationUrl(targetUrl)) {
            return null
        }
        val uri = targetUrl.toUri()
        if (isPdfViewerUrl(uri)) {
            if (depth++ >= MAX_WRAPPED_URL_DEPTH) {
                return null
            }
            targetUrl =
                uri.getQueryParameter("wk_pdf_url")?.takeIf { it.isNotBlank() }
                    ?: return null
            continue
        }
        val (schemeType, blockCause) =
            getBlockInfo(targetUrl, blacklistRegexes, whitelistRegexes, userSettings)
        if (blockCause != null || !isCustomBlockPageUrl(schemeType, uri)) {
            return ResolvedBlockPageTarget(targetUrl, schemeType, blockCause)
        }
        if (depth++ >= MAX_WRAPPED_URL_DEPTH) {
            return null
        }
        targetUrl =
            uri.getQueryParameter("url")?.takeIf { it.isNotBlank() } ?: return null
    }
}

fun isPdfViewerUrl(uri: Uri): Boolean {
    val viewerUri = Constants.PDF_JS_ASSETS_DUMMY_URL.toUri()
    return uri.scheme.equals(viewerUri.scheme, ignoreCase = true) &&
        uri.host.equals(viewerUri.host, ignoreCase = true) &&
        uri.port in setOf(-1, 443) &&
        uri.userInfo == null &&
        uri.path.orEmpty() in setOf("", "/") &&
        uri.getQueryParameter("wk_pdf_url") != null
}

fun loadBlockedPage(
    webView: WebView?,
    userSettings: UserSettings,
    url: String,
    blockCause: BlockCause,
) {
    val displayUrl =
        if (blockCause == BlockCause.INVALID_URL) {
            url.take(2048)
        } else {
            url
        }
    val baseUrl =
        if (blockCause == BlockCause.INVALID_URL) {
            "about:blank"
        } else if (url.toUri().scheme !in setOf("http", "https", "file")) {
            "${Constants.APP_SCHEME}://${BLOCK_HOST}?cause=${blockCause.name}&url=${URLEncoder.encode(url, "UTF-8")}"
        } else {
            url
        }

    webView?.loadDataWithBaseURL(
        baseUrl,
        blockedPageHtml(userSettings, displayUrl, blockCause),
        "text/html",
        "UTF-8",
        null,
    )
}

fun blockedPageHtml(
    userSettings: UserSettings,
    url: String,
    blockCause: BlockCause,
): String =
    userSettings.customBlockPageHtml.ifBlank {
        generateBlockedPageHtml(userSettings.theme, blockCause, userSettings, url)
    }

fun isCustomBlockPageUrl(schemeType: SchemeType, uri: Uri): Boolean {
    return schemeType == SchemeType.APP_CUSTOM && uri.host == BLOCK_HOST
}
