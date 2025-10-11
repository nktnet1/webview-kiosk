package uk.nktnet.webviewkiosk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.utils.webview.generateBlockedPageHtml
import uk.nktnet.webviewkiosk.utils.webview.generateDesktopViewportScript
import uk.nktnet.webviewkiosk.utils.webview.generatePrefersColorSchemeOverrideScript
import uk.nktnet.webviewkiosk.utils.webview.handleExternalScheme
import uk.nktnet.webviewkiosk.utils.webview.isBlockedUrl
import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE

data class WebViewConfig(
    val userSettings: UserSettings,
    val theme: ThemeOption,
    val onPageStarted: () -> Unit,
    val onPageFinished: (String) -> Unit,
    val doUpdateVisitedHistory: (String) -> Unit,
    val onHttpAuthRequest: (HttpAuthHandler?, String?, String?) -> Unit,
    val onLinkLongClick: (String) -> Unit
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
                layoutAlgorithm = userSettings.layoutAlgorithm.algorithm
                useWideViewPort = userSettings.useWideViewPort
                loadWithOverviewMode = userSettings.loadWithOverviewMode
                builtInZoomControls = userSettings.enableZoom
                displayZoomControls = userSettings.displayZoomControls
                allowFileAccess = userSettings.allowLocalFiles
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = userSettings.allowFileAccessFromFileURLs
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = userSettings.allowUniversalAccessFromFileURLs
                mediaPlaybackRequiresUserGesture = userSettings.mediaPlaybackRequiresUserGesture
            }

            val isBlocked: (String) -> Boolean = { url ->
                isBlockedUrl(url, config.blacklistRegexes, config.whitelistRegexes)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (userSettings.applyAppTheme && config.theme != ThemeOption.SYSTEM) {
                        evaluateJavascript(generatePrefersColorSchemeOverrideScript(config.theme), null)
                    }
                    if (userSettings.customScriptOnPageStart.isNotBlank()) {
                        view?.evaluateJavascript(wrapJsInIIFE(userSettings.customScriptOnPageStart), null)
                    }
                    super.onPageStarted(view, url, favicon)
                    config.onPageStarted()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (userSettings.applyDesktopViewportWidth >= Constants.MIN_DESKTOP_WIDTH) {
                        view?.evaluateJavascript(generateDesktopViewportScript(userSettings.applyDesktopViewportWidth), null)
                    }
                    if (userSettings.customScriptOnPageFinish.isNotBlank()) {
                        view?.evaluateJavascript(wrapJsInIIFE(userSettings.customScriptOnPageFinish), null)
                    }
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

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    val grantedResources = mutableListOf<String>()

                    request.resources.forEach { res ->
                        when (res) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> if (userSettings.allowCamera) {
                                grantedResources.add(res)
                            }
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> if (userSettings.allowMicrophone) {
                                grantedResources.add(res)
                            }
                        }
                    }

                    if (grantedResources.isNotEmpty()) {
                        request.grant(grantedResources.toTypedArray())
                    } else {
                        request.deny()
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, userSettings.allowLocation, false)
                }
            }

            if (userSettings.allowLinkLongPressContextMenu) {
                setOnLongClickListener {
                    val result = hitTestResult
                    if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                        result.extra?.let { link -> config.onLinkLongClick(link) }
                        false
                    } else {
                        true
                    }
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
