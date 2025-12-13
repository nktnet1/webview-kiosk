package com.nktnet.webview_kiosk.utils

fun normaliseInfoText(text: String): String {
    return text
        .replace("\t", "    ")
        .replace(
            Regex("(?m)(?<!\\n)\\n(?!\\n)(?!\\s*- )(?!\\s*\\d+\\. )(?!\\s)"),
            " "
        )
}
