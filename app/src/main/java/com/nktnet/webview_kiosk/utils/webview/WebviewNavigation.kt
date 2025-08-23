package com.nktnet.webview_kiosk.utils.webview

import android.webkit.WebView
import com.nktnet.webview_kiosk.config.SystemSettings

object WebViewNavigation {
    fun goBack(webView: WebView, systemSettings: SystemSettings) {
        val index = systemSettings.historyIndex
        if (index > 0) {
            val newIndex = index - 1
            val url = systemSettings.historyStack[newIndex]
            systemSettings.historyIndex = newIndex
            webView.loadUrl(url)
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

    fun goHome(webView: WebView, systemSettings: SystemSettings, homeUrl: String) {
        webView.loadUrl(homeUrl)
        systemSettings.historyStack = listOf(homeUrl)
        systemSettings.historyIndex = 0
    }
}
