package uk.nktnet.webviewkiosk.utils.webview

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
