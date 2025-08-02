package com.example.webview_locker.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import com.example.webview_locker.config.SystemSettings
import com.example.webview_locker.config.UserSettings

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun customWebView(
    context: Context,
    initUrl: String
): WebView {
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val blacklistText by remember { derivedStateOf { userSettings.websiteBlacklist } }
    val whitelistText by remember { derivedStateOf { userSettings.websiteWhitelist } }

    val blacklistRegexes by remember(blacklistText) {
        mutableStateOf(
            blacklistText.lines()
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .mapNotNull { runCatching { Regex(it) }.getOrNull() }
        )
    }
    val whitelistRegexes by remember(whitelistText) {
        mutableStateOf(
            whitelistText.lines()
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .mapNotNull { runCatching { Regex(it) }.getOrNull() }
        )
    }

    fun isBlocked(url: String): Boolean {
        if (whitelistRegexes.any { it.containsMatchIn(url) }) return false
        return blacklistRegexes.any { it.containsMatchIn(url) }
    }

    fun showBlockedPage(view: WebView?, url: String) {
        view?.apply {
            loadUrl("about:blank")
            post {
                loadData(
                    """
                    <html>
                        <body style="text-align:center;font-family:sans-serif;padding-top:50px">
                            <h2>🚫 Access Blocked</h2>
                            <p>This site is blocked by WebView Locker.</p>
                            <p><code>$url</code></p>
                        </body>
                    </html>
                    """.trimIndent(),
                    "text/html",
                    "UTF-8"
                )
            }
        }
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url.toString()
                    return if (isBlocked(url)) {
                        showBlockedPage(view, url)
                        true
                    } else {
                        false
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    url?.let {
                        if (!isBlocked(it)) {
                            systemSettings.lastUrl = it
                        }
                    }
                }
            }

            loadUrl(initUrl)
            if (!isBlocked(initUrl)) {
                systemSettings.lastUrl = initUrl
            }
        }
    }

    LaunchedEffect(blacklistRegexes, whitelistRegexes) {
        val currentUrl = webView.url.orEmpty()
        if (isBlocked(currentUrl)) {
            showBlockedPage(webView, currentUrl)
        } else {
            val lastUrl = systemSettings.lastUrl.takeIf { it.isNotEmpty() } ?: initUrl
            webView.loadUrl(lastUrl)
        }
    }

    return webView
}
