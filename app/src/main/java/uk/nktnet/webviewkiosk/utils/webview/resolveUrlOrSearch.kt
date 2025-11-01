package uk.nktnet.webviewkiosk.utils.webview

import android.net.Uri
import android.webkit.URLUtil

fun resolveUrlOrSearch(searchProviderUrl: String, input: String): String {
    val trimmed = input.trim()
    if (URLUtil.isValidUrl(trimmed)) {
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
