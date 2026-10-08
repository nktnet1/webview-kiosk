package uk.nktnet.webviewkiosk.utils.webview

import android.net.Uri
import android.webkit.WebView
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.isLocalFileLink
import uk.nktnet.webviewkiosk.utils.webview.html.BlockCause
import uk.nktnet.webviewkiosk.utils.webview.html.generateBlockedPageHtml
import java.net.URLEncoder

const val BLOCK_HOST = "block"
private const val MAX_WRAPPED_URL_DEPTH = 10

enum class SchemeType {
    FILE,
    WEB,
    DATA,
    APP_CUSTOM,
    OTHER
}

fun isBlockedUrl(
    url: String,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>
): Boolean {
    return !whitelistRegexes.any {
        it.containsMatchIn(url)
    } && blacklistRegexes.any {
        it.containsMatchIn(url)
    }
}

fun getBlockInfo(
    url: String,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>,
    userSettings: UserSettings
): Pair<SchemeType, BlockCause?> {
    val uri = url.toUri()
    val scheme = uri.scheme?.lowercase() ?: ""
    val schemeType = when (scheme) {
        "file" -> SchemeType.FILE
        "http", "https" -> SchemeType.WEB
        "data" -> SchemeType.DATA
        Constants.APP_SCHEME -> SchemeType.APP_CUSTOM
        else -> SchemeType.OTHER
    }

    val blockCause = when {
        isBlockedUrl(url, blacklistRegexes, whitelistRegexes) -> BlockCause.BLACKLIST
        (schemeType == SchemeType.FILE || uri.isLocalFileLink())
            && !userSettings.allowLocalFiles -> BlockCause.LOCAL_FILE
        else -> null
    }
    return schemeType to blockCause
}

fun resolveBlockPageUrl(
    url: String,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>,
    userSettings: UserSettings,
): String? {
    var targetUrl = url
    var depth = 0
    while (true) {
        val uri = targetUrl.toUri()
        if (isPdfViewerUrl(uri)) {
            // The viewer is an internal document. Apply URL policy to its source.
            if (depth++ >= MAX_WRAPPED_URL_DEPTH) {
                return null
            }
            targetUrl = uri.getQueryParameter("wk_pdf_url")
                ?.takeIf { it.isNotBlank() } ?: return null
            continue
        }
        val (schemeType, blockCause) = getBlockInfo(
            targetUrl, blacklistRegexes, whitelistRegexes, userSettings
        )
        // Keep a denied URL so the caller can apply its usual block action.
        if (blockCause != null || !isCustomBlockPageUrl(schemeType, uri)) {
            return targetUrl
        }
        // Do not let malformed or excessively nested wrappers trigger unchecked loads.
        if (depth++ >= MAX_WRAPPED_URL_DEPTH) {
            return null
        }
        targetUrl = uri.getQueryParameter("url")?.takeIf { it.isNotBlank() } ?: return null
    }
}

fun isPdfViewerUrl(uri: Uri): Boolean {
    val viewerUri = Constants.PDF_JS_ASSETS_DUMMY_URL.toUri()
    return uri.scheme.equals(viewerUri.scheme, ignoreCase = true)
        && uri.host.equals(viewerUri.host, ignoreCase = true)
        && uri.port in setOf(-1, 443)
        && uri.userInfo == null
        && uri.path.orEmpty() in setOf("", "/")
        && uri.getQueryParameter("wk_pdf_url") != null
}

fun loadBlockedPage(
    webView: WebView?,
    userSettings: UserSettings,
    url: String,
    blockCause: BlockCause,
) {
    val baseUrl = if ( url.toUri().scheme !in setOf("http", "https", "file")) {
        "${Constants.APP_SCHEME}://${BLOCK_HOST}?cause=${blockCause.name}&url=${URLEncoder.encode(url, "UTF-8")}"
    } else {
        url
    }

    val html = userSettings.customBlockPageHtml.ifBlank {
        generateBlockedPageHtml(
            userSettings.theme,
            blockCause,
            userSettings,
            url
        )
    }
    webView?.loadDataWithBaseURL(
        baseUrl,
        html,
        "text/html",
        "UTF-8",
        null
    )
}

fun isCustomBlockPageUrl(schemeType: SchemeType, uri: Uri): Boolean {
    return schemeType == SchemeType.APP_CUSTOM && uri.host == BLOCK_HOST
}
