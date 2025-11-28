package com.nktnet.webview_kiosk.utils

import android.util.Patterns
import android.webkit.URLUtil.isValidUrl
import androidx.core.net.toUri
import java.io.File

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
            val filePath = URLDecoder.decode(
                trimmedInput.removePrefix("file://"),
                StandardCharsets.UTF_8.name()
            )
            File(filePath).exists()
        }
        "http" -> {
            isValidUrl(trimmedInput)
        }
        "https" -> {
            isValidUrl(trimmedInput)
            && (
                Patterns.WEB_URL.matcher(trimmedInput).matches()
                || uri.host?.matches(Regex("""\[[0-9a-fA-F:]+]""")) == true
            )
        }
        "data" -> {
            isDataSchemeUrl(trimmedInput)
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
