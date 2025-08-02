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
                .mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() } }
                .mapNotNull { pattern -> runCatching { Regex(pattern) }.getOrNull() }
        )
    }
    val whitelistRegexes by remember(whitelistText) {
        mutableStateOf(
            whitelistText.lines()
                .mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() } }
                .mapNotNull { pattern -> runCatching { Regex(pattern) }.getOrNull() }
        )
    }

    var lastNonBlockedUrl by remember { mutableStateOf(initUrl) }

    fun isBlocked(url: String): Boolean {
        return when {
            whitelistRegexes.isNotEmpty() -> whitelistRegexes.none { it.containsMatchIn(url) }
            else -> blacklistRegexes.any { it.containsMatchIn(url) }
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
                        view?.loadData(
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
                        true
                    } else {
                        false
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != null && !isBlocked(url)) {
                        lastNonBlockedUrl = url
                        systemSettings.lastUrl = url
                    }
                }
            }

            loadUrl(initUrl)
        }
    }

    LaunchedEffect(blacklistRegexes, whitelistRegexes) {
        webView.clearCache(true)
        if (isBlocked(webView.url ?: "")) {
            webView.loadData(
                """
                <html>
                    <body style="text-align:center;font-family:sans-serif;padding-top:50px">
                        <h2>🚫 Access Blocked</h2>
                        <p>This site is blocked by WebView Locker.</p>
                        <p><code>${webView.url}</code></p>
                    </body>
                </html>
                """.trimIndent(),
                "text/html",
                "UTF-8"
            )
        } else {
            webView.loadUrl(lastNonBlockedUrl)
        }
    }

    return webView
}
