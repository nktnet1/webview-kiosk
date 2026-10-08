package uk.nktnet.webviewkiosk.utils

import android.util.Patterns
import android.webkit.URLUtil.isValidUrl
import androidx.core.net.toUri
import java.io.File
import java.net.URI
import java.net.URISyntaxException

fun isDataSchemeUrl(url: String): Boolean {
    val dataUrlRegex = Regex(
        """^data:(?:[a-zA-Z0-9!#$&.+\-^_]+/[a-zA-Z0-9!#$&.+\-^_]+)?(?:;base64)?,.*"""
    )
    return url.startsWith("data:") && dataUrlRegex.matches(url)
}

fun validateUrl(input: String): Boolean {
    if (input.isEmpty()) {
        return true
    }
    val trimmedInput = input.trim()
    val uri = trimmedInput.toUri()
    return when (uri.scheme) {
        "file" -> {
            uri.path?.let {
                File(it).exists()
            } ?: false
        }
        "http" -> {
            isValidUrl(trimmedInput)
        }
        "https" -> {
            isValidUrl(trimmedInput)
            && (
                Patterns.WEB_URL.matcher(trimmedInput).matches()
                || isIpv6Url(trimmedInput)
                || uri.host.equals("localhost", ignoreCase = true)
            )
        }
        "data" -> {
            isDataSchemeUrl(trimmedInput)
        }
        else -> false
    }
}

private fun isIpv6Url(url: String): Boolean {
    // Older Android Uri implementations split IPv6 hosts at the first colon.
    return try {
        URI(url).host?.startsWith("[") == true
    } catch (_: URISyntaxException) {
        false
    }
}

fun validateMultilineRegex(text: String): Boolean {
    return text.lines().all { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@all true
        try {
            Regex(trimmed)
            true
        } catch (_: Exception) {
            false
        }
    }
}
