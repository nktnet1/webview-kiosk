package com.nktnet.webview_kiosk.utils

import android.webkit.URLUtil.isValidUrl

import java.net.URL

fun validateUrl(input: String): Boolean {
    if (input.isEmpty()) {
        return true
    }
    if (!(input.startsWith("http://") || input.startsWith("https://"))) {
        return false
    }
    return try {
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
