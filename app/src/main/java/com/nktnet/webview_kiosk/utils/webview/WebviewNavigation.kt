package com.nktnet.webview_kiosk.utils.webview

import android.webkit.WebView
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings

object WebViewNavigation {
    fun goBack(webView: WebView, systemSettings: SystemSettings) {
        val index = systemSettings.historyIndex
        if (index > 0) {
            val newIndex = index - 1
            systemSettings.historyIndex = newIndex
            webView.loadUrl(systemSettings.historyStack[newIndex])
        }
    }

    fun goForward(webView: WebView, systemSettings: SystemSettings) {
        val index = systemSettings.historyIndex
        if (index < systemSettings.historyStack.lastIndex) {
            val newIndex = index + 1
            val url = systemSettings.historyStack[newIndex]
            systemSettings.historyIndex = newIndex
            webView.loadUrl(url)
        }
    }

    fun goHome(
        webView: WebView,
        systemSettings: SystemSettings,
        userSettings: UserSettings,
    ) {
        webView.loadUrl(userSettings.homeUrl)
        if (userSettings.clearHistoryOnHome) {
            systemSettings.historyStack = listOf(userSettings.homeUrl)
            systemSettings.historyIndex = 0
        } else {
            val stack = systemSettings.historyStack.toMutableList()
            if (stack.lastOrNull() != userSettings.homeUrl) {
                systemSettings.historyStack = stack
                systemSettings.historyIndex = stack.lastIndex
            } else {
                systemSettings.historyIndex = stack.lastIndex
            }
        }
    }
}
