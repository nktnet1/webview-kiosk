package uk.nktnet.webviewkiosk.utils

import android.util.Patterns
import android.webkit.URLUtil.isValidUrl
import java.io.File

import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun validateUrl(input: String): Boolean {
    if (input.isEmpty()) {
        return true
    }
    val trimmedInput = input.trim()

    val parsedUrl = runCatching { URL(trimmedInput) }.getOrElse { return false }
    return when (parsedUrl.protocol) {
        "file" -> {
            val filePath = URLDecoder.decode(trimmedInput.removePrefix("file:///"), StandardCharsets.UTF_8.name())
            File(filePath).exists()
        }
        "http", "https" -> {
            isValidUrl(trimmedInput)
            && (
                Patterns.WEB_URL.matcher(trimmedInput).matches()
                || parsedUrl.host == "localhost"
                || parsedUrl.host.matches(Regex("""\[[0-9a-fA-F:]+]"""))
            )
        }
        else -> false
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
