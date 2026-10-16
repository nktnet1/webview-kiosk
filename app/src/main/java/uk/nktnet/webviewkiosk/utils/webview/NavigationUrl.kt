package uk.nktnet.webviewkiosk.utils.webview

/**
 * Treat an optional slash at the end of a URL's path as equivalent, without
 * discarding meaningful trailing slashes in query parameters or SPA fragments. This
 * is intentionally not a general-purpose URL canonicalizer.
 */
internal fun normalizedNavigationUrl(url: String): String {
    val suffixStart =
        url.indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 } ?: url.length
    return url.substring(0, suffixStart).trimEnd('/') + url.substring(suffixStart)
}

internal fun sameNavigationUrl(first: String?, second: String?): Boolean =
    first != null &&
        second != null &&
        normalizedNavigationUrl(first) == normalizedNavigationUrl(second)
