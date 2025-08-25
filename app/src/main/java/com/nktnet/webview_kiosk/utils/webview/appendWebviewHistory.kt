package com.nktnet.webview_kiosk.utils.webview

import com.nktnet.webview_kiosk.config.SystemSettings

fun appendWebviewHistory(systemSettings: SystemSettings, url: String) {
    val stack = systemSettings.historyStack.toMutableList()
    val newUrl = url.trimEnd('/')
    val currentIndex = systemSettings.historyIndex
    val currentUrl = stack.getOrNull(currentIndex)?.trimEnd('/')

    if (currentUrl != newUrl) {
        if (currentIndex in stack.indices && currentIndex < stack.lastIndex) {
            stack.subList(currentIndex + 1, stack.size).clear()
        }
        stack.add(newUrl)
        systemSettings.historyStack = stack
        systemSettings.historyIndex = stack.lastIndex
    } else {
        systemSettings.historyIndex = currentIndex
    }

    println("WebView History Stack:")
    stack.forEachIndexed { i, s ->
        val marker = if (i == systemSettings.historyIndex) "->" else "  "
        println("$i: $marker $s")
    }
}
