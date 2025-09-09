package uk.nktnet.webviewkiosk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.utils.webview.generateBlockedPageHtml
import uk.nktnet.webviewkiosk.utils.webview.getPrefersColorSchemeOverrideScript
import uk.nktnet.webviewkiosk.utils.webview.handleExternalScheme
import uk.nktnet.webviewkiosk.utils.webview.isBlockedUrl

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun createCustomWebview(
    context: Context,
    theme: ThemeOption,
    blockedMessage: String,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>,
    allowOtherUrlSchemes: Boolean,
    enableJavaScript: Boolean,
    enableDomStorage: Boolean,
    cacheMode: Int,
    onPageStarted: () -> Unit,
    onPageFinished: (url: String) -> Unit,
    doUpdateVisitedHistory: (url: String) -> Unit
): WebView {
    val isBlocked: (String) -> Boolean = { url ->
        isBlockedUrl(url = url, blacklistRegexes = blacklistRegexes, whitelistRegexes = whitelistRegexes)
    }

    var isShowingBlockedPage by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.javaScriptEnabled = enableJavaScript
            settings.domStorageEnabled = enableDomStorage
            settings.cacheMode = cacheMode

            webViewClient = object : WebViewClient() {
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
                    isShowingBlockedPage = false
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: ""
                    val scheme = request?.url?.scheme?.lowercase() ?: ""

                    if (scheme !in listOf("http", "https")) {
                        if (!allowOtherUrlSchemes) {
                            view?.loadBlockedPage(url, blockedMessage, theme)
                            isShowingBlockedPage = true
                            return true
                        }
                        handleExternalScheme(context, url)
                        return true
                    }

                    if (!isShowingBlockedPage && isBlocked(url)) {
                        view?.loadBlockedPage(url, blockedMessage, theme)
                        isShowingBlockedPage = true
                        return true
                    }

                    return false
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    url?.let {
                        val isUrlBlocked = isBlocked(it)
                        if (!isShowingBlockedPage && isUrlBlocked) {
                            isShowingBlockedPage = true
                            view?.loadBlockedPage(it, blockedMessage, theme)
                            return
                        }
                        doUpdateVisitedHistory(it)
                    }
                    super.doUpdateVisitedHistory(view, url, isReload)
                }
            }
        }
    }

    return webView
}

private fun WebView.loadBlockedPage(
    url: String,
    message: String,
    theme: ThemeOption,
) {
    loadDataWithBaseURL(
        url,
        generateBlockedPageHtml(url, message, theme),
        "text/html",
        "UTF-8",
        null
    )
}
