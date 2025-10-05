package uk.nktnet.webviewkiosk.utils

import android.webkit.URLUtil.isValidUrl
import java.io.File

import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun validateUrl(input: String): Boolean {
    if (input.isEmpty()) {
        return true
    }

    return when {
        input.startsWith("file:///") -> {
            val filePath = URLDecoder.decode(input.removePrefix("file:///"), StandardCharsets.UTF_8.name())
            File(filePath).exists()
        }
        input.startsWith("http://") || input.startsWith("https://") -> {
            try {
                val inputUrl = URL(input)
                val host = inputUrl.host
                host.contains(".") &&
                        host.substringAfterLast(".").matches(Regex("^[a-zA-Z]{2,}$")) &&
                        (inputUrl.protocol == "http" || inputUrl.protocol == "https") &&
                        isValidUrl(input)
            } catch (_: Exception) {
                false
            }
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
