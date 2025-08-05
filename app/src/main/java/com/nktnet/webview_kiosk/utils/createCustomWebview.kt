package com.nktnet.webview_kiosk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun createCustomWebview(
    context: Context,
    initUrl: String,
    isBlocked: (String) -> Boolean,
    showBlockedPage: (String) -> String,
    onPageFinished: (String) -> Unit
): WebView {
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
                        view?.post {
                            view.loadDataWithBaseURL(
                                url,
                                showBlockedPage(url),
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                        true
                    } else {
                        false
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    url?.let { onPageFinished(it) }
                }
            }

            loadUrl(initUrl)
            if (!isBlocked(initUrl)) {
                onPageFinished(initUrl)
            }
        }
    }

    LaunchedEffect(isBlocked, showBlockedPage) {
        val currentUrl = webView.url.orEmpty()
        if (isBlocked(currentUrl)) {
            webView.post {
                webView.loadDataWithBaseURL(
                    currentUrl,
                    showBlockedPage(currentUrl),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        } else {
            webView.loadUrl(currentUrl.ifEmpty { initUrl })
        }
    }

    return webView
}
