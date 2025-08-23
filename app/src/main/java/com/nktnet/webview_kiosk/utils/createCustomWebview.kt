package com.nktnet.webview_kiosk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.Html
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import com.nktnet.webview_kiosk.config.option.ThemeOption

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun createCustomWebview(
    context: Context,
    theme: ThemeOption,

    blockedMessage: String,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>,

    onPageStarted: () -> Unit,
    onPageFinished: (String) -> Unit
): WebView {
    val isBlocked: (String) -> Boolean = { url ->
        isBlockedUrl(url = url, blacklistRegexes = blacklistRegexes, whitelistRegexes = whitelistRegexes)
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
                    val url = request?.url?.toString() ?: ""
                    return if (isBlocked(url)) {
                        view?.loadBlockedPage(url, blockedMessage)
                        true
                    } else {
                        false
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (theme != ThemeOption.SYSTEM) {
                        val js = getPrefersColorSchemeOverrideScript(theme)
                        evaluateJavascript(js, null)
                    }
                    super.onPageStarted(view, url, favicon)
                    onPageStarted()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    url?.let { onPageFinished(it) }
                }
            }
        }
    }

    return webView
}

fun WebView.loadBlockedPage(
    url: String,
    message: String,
) {
    loadDataWithBaseURL(
        url,
        generateBlockedPageHtml(url, message),
        "text/html",
        "UTF-8",
        null
    )
}

fun WebView.customLoadUrl(
    url: String,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>,
    blockedMessage: String,
) {
    if (isBlockedUrl(url, blacklistRegexes, whitelistRegexes)) {
        loadBlockedPage(url, blockedMessage)
    } else {
        loadUrl(url)
    }
}

fun isBlockedUrl(
    url: String,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>
): Boolean {
    return if (whitelistRegexes.any { it.containsMatchIn(url) }) {
        false
    } else {
        blacklistRegexes.any { it.containsMatchIn(url) }
    }
}

fun generateBlockedPageHtml(url: String, message: String): String {
    return """
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
                hr {
                  border: none;
                  border-top: 1px solid #555555;
                  margin: 20px 0 30px 0px;
                }
            </style>
          </head>
          <body>
            <h2>🚫 Access Blocked</h2>
            <p>${Html.escapeHtml(message)}</p>
            <hr />
            <b>URL:</b>
            <p>${Html.escapeHtml(url)}</p>
          </body>
        </html>
    """.trimIndent()
}


fun resolveUrlOrSearch(searchProviderUrl: String, input: String): String {
    return when {
        URLUtil.isValidUrl(input) -> input
        input.contains('.') -> "https://$input"
        else -> "${searchProviderUrl}${Uri.encode(input)}"
    }
}

private fun getPrefersColorSchemeOverrideScript(theme: ThemeOption): String {
    if (theme == ThemeOption.SYSTEM) {
        return ""
    }

    val mediaValue = when (theme) {
        ThemeOption.DARK -> "dark"
        ThemeOption.LIGHT -> "light"
        else -> error("Unhandled theme: $theme")
    }

    return """
        (function() {
            window.matchMedia = (function(origMatchMedia) {
                return function(query) {
                    if (query === '(prefers-color-scheme: dark)') {
                        return {
                            matches: ${mediaValue == "dark"},
                            media: query,
                            addListener: function() {},
                            removeListener: function() {},
                            onchange: null,
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return false; }
                        };
                    }
                    if (query === '(prefers-color-scheme: light)') {
                        return {
                            matches: ${mediaValue == "light"},
                            media: query,
                            addListener: function() {},
                            removeListener: function() {},
                            onchange: null,
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return false; }
                        };
                    }
                    return origMatchMedia.call(window, query);
                };
            })(window.matchMedia);
        })();
    """.trimIndent()
}
