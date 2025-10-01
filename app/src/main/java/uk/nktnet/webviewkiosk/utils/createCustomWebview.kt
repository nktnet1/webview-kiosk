package uk.nktnet.webviewkiosk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.utils.webview.generateBlockedPageHtml
import uk.nktnet.webviewkiosk.utils.webview.getPrefersColorSchemeOverrideScript
import uk.nktnet.webviewkiosk.utils.webview.handleExternalScheme
import uk.nktnet.webviewkiosk.utils.webview.isBlockedUrl

data class WebViewConfig(
    val userSettings: UserSettings,
    val theme: ThemeOption,
    val onPageStarted: () -> Unit,
    val onPageFinished: (String) -> Unit,
    val doUpdateVisitedHistory: (String) -> Unit,
    val onHttpAuthRequest: (HttpAuthHandler?, String?, String?) -> Unit
) {
    val blacklistRegexes: List<Regex> by lazy {
        userSettings.websiteBlacklist.lines()
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }
    }

    val whitelistRegexes: List<Regex> by lazy {
        userSettings.websiteWhitelist.lines()
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun createCustomWebview(
    context: Context,
    config: WebViewConfig
): WebView {
    val userSettings = config.userSettings
    var isShowingBlockedPage by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = userSettings.enableJavaScript
                domStorageEnabled = userSettings.enableDomStorage
                cacheMode = userSettings.cacheMode.mode
                userAgentString = userSettings.userAgent.takeIf { it.isNotBlank() }
                    ?: settings.userAgentString
                builtInZoomControls = userSettings.enableZoom
                displayZoomControls = userSettings.displayZoomControls
            }

            val isBlocked: (String) -> Boolean = { url ->
                isBlockedUrl(url, config.blacklistRegexes, config.whitelistRegexes)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (config.theme != ThemeOption.SYSTEM) {
                        evaluateJavascript(getPrefersColorSchemeOverrideScript(config.theme), null)
                    }
                    super.onPageStarted(view, url, favicon)
                    config.onPageStarted()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    url?.let { config.onPageFinished(it) }
                    isShowingBlockedPage = false
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: ""
                    val scheme = request?.url?.scheme?.lowercase() ?: ""

                    if (scheme !in listOf("http", "https")) {
                        if (!userSettings.allowOtherUrlSchemes) {
                            view?.loadBlockedPage(url, userSettings.blockedMessage, config.theme)
                            isShowingBlockedPage = true
                            return true
                        }
                        handleExternalScheme(context, url)
                        return true
                    }

                    if (!isShowingBlockedPage && isBlocked(url)) {
                        view?.loadBlockedPage(url, userSettings.blockedMessage, config.theme)
                        isShowingBlockedPage = true
                        return true
                    }

                    return false
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    url?.let {
                        if (!isShowingBlockedPage && isBlocked(it)) {
                            isShowingBlockedPage = true
                            view?.loadBlockedPage(it, userSettings.blockedMessage, config.theme)
                            return
                        }
                        config.doUpdateVisitedHistory(it)
                    }
                    super.doUpdateVisitedHistory(view, url, isReload)
                }

                override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
                    config.onHttpAuthRequest(handler, host, realm)
                }
            }
        }
    }

    return webView
}

private fun WebView.loadBlockedPage(url: String, message: String, theme: ThemeOption) {
    loadDataWithBaseURL(
        url,
        generateBlockedPageHtml(url, message, theme),
        "text/html",
        "UTF-8",
        null
    )
}
