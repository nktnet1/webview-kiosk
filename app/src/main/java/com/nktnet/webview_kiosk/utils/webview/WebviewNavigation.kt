package com.nktnet.webview_kiosk.utils.webview

import android.webkit.WebView
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings

object WebViewNavigation {
    private var isProgrammaticNavigation = false

    fun goBack(webView: WebView, systemSettings: SystemSettings) {
        val index = systemSettings.historyIndex
        if (index > 0) {
            val newIndex = index - 1
            systemSettings.historyIndex = newIndex
            isProgrammaticNavigation = true
            webView.loadUrl(systemSettings.historyStack[newIndex])
        }
    }

    fun goForward(webView: WebView, systemSettings: SystemSettings) {
        val index = systemSettings.historyIndex
        if (index < systemSettings.historyStack.lastIndex) {
            val newIndex = index + 1
            systemSettings.historyIndex = newIndex
            isProgrammaticNavigation = true
            webView.loadUrl(systemSettings.historyStack[newIndex])
        }
    }

    fun goHome(
        webView: WebView,
        systemSettings: SystemSettings,
        userSettings: UserSettings,
    ) {
        if (userSettings.clearHistoryOnHome) {
            systemSettings.historyStack = listOf(userSettings.homeUrl)
            systemSettings.historyIndex = 0
        }

        if (systemSettings.lastUrl != userSettings.homeUrl) {
            webView.loadUrl(userSettings.homeUrl)
        }
    }

    fun appendWebviewHistory(systemSettings: SystemSettings, url: String) {
        if (isProgrammaticNavigation) {
            isProgrammaticNavigation = false
            return
        }

        val newUrl = url.trimEnd('/')
        val stack = systemSettings.historyStack.toMutableList()
        val currentIndex = systemSettings.historyIndex
        val currentUrl = stack.getOrNull(currentIndex)?.trimEnd('/')

        if (currentUrl != newUrl) {
            val updatedStack = if (currentIndex < stack.lastIndex) {
                stack.subList(0, currentIndex + 1).toMutableList()
            } else {
                stack
            }

            updatedStack.add(newUrl)
            systemSettings.historyStack = updatedStack
            systemSettings.historyIndex = updatedStack.lastIndex
        } else {
            systemSettings.historyIndex = currentIndex
        }

//        println("[HISTORY] WebView Stack:")
//        systemSettings.historyStack.forEachIndexed { i, s ->
//            val marker = if (i == systemSettings.historyIndex) "->" else "  "
//            println("[HISTORY] $i: $marker $s")
//        }
    }
}
