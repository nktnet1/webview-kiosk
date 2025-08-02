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
    val blockedMessage by remember { derivedStateOf { userSettings.blockedMessage } }

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
            post {
                loadDataWithBaseURL(
                    url,
                    """
                <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
                        <style>
                            body {
                                margin: 0;
                                padding-top: 50px;
                                padding-left: 20px;
                                padding-right: 20px;
                                font-family: sans-serif;
                                overflow-wrap: break-word;
                                box-sizing: border-box;
                                display: flex;
                                flex-direction: column;
                                text-align: center;
                                justify-content: center;
                                white-space: pre-wrap;
                            }
                        </style>
                    </head>
                    <body>
                        <h2>🚫 Access Blocked</h2>
                        <p>$blockedMessage</p>
                        <p>$url</p>
                        <!-- Forced error: unclosed <div> tag to break rendering -->
                        <div>
                    </body>
                </html>
                """.trimIndent(),
                    "text/html",
                    "UTF-8",
                    null,
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
                        systemSettings.lastUrl = it
                    }
                }
            }

            loadUrl(initUrl)
            if (!isBlocked(initUrl)) {
                systemSettings.lastUrl = initUrl
            }
        }
    }

    LaunchedEffect(blacklistRegexes, whitelistRegexes, blockedMessage) {
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
