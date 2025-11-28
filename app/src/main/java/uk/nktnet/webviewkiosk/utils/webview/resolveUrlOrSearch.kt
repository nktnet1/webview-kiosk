package com.nktnet.webview_kiosk.utils.webview

import android.net.Uri
import android.webkit.URLUtil
import com.nktnet.webview_kiosk.utils.isDataSchemeUrl

fun resolveUrlOrSearch(searchProviderUrl: String, input: String): String {
    val trimmed = input.trim()
    if (URLUtil.isValidUrl(trimmed) || isDataSchemeUrl(trimmed)) {
        return trimmed
    }
    val domainRegex = Regex("""^(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(?:/.*)?$""")
    if (domainRegex.matches(trimmed)) {
        return "https://$trimmed"
    }
    val ipWithOptionalPortRegex = Regex("""^(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?(?:/.*)?$""")
    val localhostWithPortRegex = Regex("""^localhost:\d+(?:/.*)?$""")
    if (ipWithOptionalPortRegex.matches(trimmed) || localhostWithPortRegex.matches(trimmed)) {
        return "http://$trimmed"
    }
    return "${searchProviderUrl}${Uri.encode(trimmed)}"
}
