package com.nktnet.webview_kiosk.utils.webview

import android.net.Uri
import android.webkit.URLUtil

fun resolveUrlOrSearch(searchProviderUrl: String, input: String): String {
    return when {
        URLUtil.isValidUrl(input) -> input
        input.contains('.') -> "https://$input"
        else -> "${searchProviderUrl}${Uri.encode(input)}"
    }
}