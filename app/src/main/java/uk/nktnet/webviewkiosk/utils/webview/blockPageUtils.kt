package uk.nktnet.webviewkiosk.utils.webview

import android.net.Uri
import android.webkit.WebView
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.webview.html.BlockCause
import uk.nktnet.webviewkiosk.utils.webview.html.generateBlockedPageHtml
import java.net.URLEncoder

const val BLOCK_HOST = "block"

enum class SchemeType {
    FILE,
    WEB,
    DATA,
    WEBVIEW_KIOSK,
    OTHER
}

fun isBlockedUrl(
    url: String,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>
): Boolean {
    return if (whitelistRegexes.any { it.containsMatchIn(url) }) {
        false
    } else {
        blacklistRegexes.any { it.containsMatchIn(url) }
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
        Constants.APP_SCHEME -> SchemeType.WEBVIEW_KIOSK
        else -> SchemeType.OTHER
    }

    val blockCause = when {
        isBlockedUrl(url, blacklistRegexes, whitelistRegexes) -> BlockCause.BLACKLIST
        schemeType == SchemeType.FILE && !userSettings.allowLocalFiles -> BlockCause.LOCAL_FILE
        else -> null
    }
    return schemeType to blockCause
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
    return schemeType == SchemeType.WEBVIEW_KIOSK && uri.host == BLOCK_HOST
}
