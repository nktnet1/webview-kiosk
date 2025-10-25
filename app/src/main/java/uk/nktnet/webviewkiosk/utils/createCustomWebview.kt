package uk.nktnet.webviewkiosk.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.utils.webview.html.BlockCause
import uk.nktnet.webviewkiosk.utils.webview.html.generateBlockedPageHtml
import uk.nktnet.webviewkiosk.utils.webview.scripts.generateDesktopViewportScript
import uk.nktnet.webviewkiosk.utils.webview.scripts.generatePrefersColorSchemeOverrideScript
import uk.nktnet.webviewkiosk.utils.webview.handlers.handleExternalScheme
import uk.nktnet.webviewkiosk.utils.webview.handlers.handleGeolocationRequest
import uk.nktnet.webviewkiosk.utils.webview.handlers.handlePermissionRequest
import uk.nktnet.webviewkiosk.utils.webview.html.generateErrorPage
import uk.nktnet.webviewkiosk.utils.webview.html.generateHttpErrorPage
import uk.nktnet.webviewkiosk.utils.webview.isBlockedUrl
import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE

data class WebViewConfig(
    val systemSettings: SystemSettings,
    val userSettings: UserSettings,
    val showToast: (message: String) -> Unit,
    val onProgressChanged: (newProgress: Int) -> Unit,
    val onPageStarted: () -> Unit,
    val onPageFinished: (String) -> Unit,
    val doUpdateVisitedHistory: (url: String, originalUrl: String?) -> Unit,
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
    val systemSettings = config.systemSettings
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

            val loadBlockedPage: (url: String, blockCause: BlockCause) -> Unit = { url, blockCause ->
                if (!isShowingBlockedPage) {
                    loadDataWithBaseURL(
                        url,
                        generateBlockedPageHtml(
                            userSettings.theme,
                            blockCause,
                            userSettings.blockedMessage,
                            url
                        ),
                        "text/html",
                        "UTF-8",
                        null
                    )
                    isShowingBlockedPage = true
                    config.onPageFinished(url)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (userSettings.applyAppTheme && userSettings.theme != ThemeOption.SYSTEM) {
                        evaluateJavascript(
                            generatePrefersColorSchemeOverrideScript(userSettings.theme),
                            null
                        )
                    }
                    if (userSettings.customScriptOnPageStart.isNotBlank()) {
                        view?.evaluateJavascript(
                            wrapJsInIIFE(userSettings.customScriptOnPageStart),
                            null
                        )
                    }

                    if (!isShowingBlockedPage) {
                        val uri = url?.toUri()
                        if (uri?.scheme == "file") {
                            if (!userSettings.allowLocalFiles) {
                                loadBlockedPage(url, BlockCause.LOCAL_FILE)
                                return
                            }
                        }
                    }
                    super.onPageStarted(view, url, favicon)
                    config.onPageStarted()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (userSettings.applyDesktopViewportWidth >= Constants.MIN_DESKTOP_WIDTH) {
                        view?.evaluateJavascript(
                            generateDesktopViewportScript(userSettings.applyDesktopViewportWidth),
                            null
                        )
                    }
                    if (userSettings.customScriptOnPageFinish.isNotBlank()) {
                        view?.evaluateJavascript(
                            wrapJsInIIFE(userSettings.customScriptOnPageFinish),
                            null
                        )
                    }
                    url?.let { config.onPageFinished(it) }
                    systemSettings.urlBeforeNavigation = ""
                    isShowingBlockedPage = false
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    if (systemSettings.urlBeforeNavigation.isEmpty()) {
                        systemSettings.urlBeforeNavigation = systemSettings.currentUrl
                    }
                    val url = request?.url?.toString() ?: ""
                    val scheme = request?.url?.scheme?.lowercase() ?: ""

                    if (!isShowingBlockedPage) {
                        if (scheme !in listOf("http", "https")) {
                            if (!userSettings.allowOtherUrlSchemes) {
                                loadBlockedPage(url, BlockCause.INTENT_URL_SCHEME)
                                return true
                            }
                            handleExternalScheme(context, url)
                            return true
                        }

                        if (isBlocked(url)) {
                            loadBlockedPage(url, BlockCause.BLACKLIST)
                            return true
                        }
                    }

                    return false
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean
                ) {
                    url?.let {
                        if (!isShowingBlockedPage) {
                            if (isBlocked(url)) {
                                loadBlockedPage(url, BlockCause.BLACKLIST)
                                return
                            }
                        }
                        config.doUpdateVisitedHistory(url, originalUrl)
                    }
                    super.doUpdateVisitedHistory(view, url, isReload)
                }

                override fun onReceivedHttpAuthRequest(
                    view: WebView?,
                    handler: HttpAuthHandler?,
                    host: String?,
                    realm: String?
                ) {
                    config.onHttpAuthRequest(handler, host, realm)
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    if (request?.isForMainFrame == true) {
                        val html = generateHttpErrorPage(userSettings.theme, request, errorResponse)
                        view?.loadDataWithBaseURL(
                            request.url.toString(),
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        return
                    }
                    super.onReceivedHttpError(view, request, errorResponse)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        val html = generateErrorPage(
                            userSettings.theme,
                            error?.errorCode,
                            error?.description?.toString(),
                            request.url?.toString()
                        )
                        view?.loadDataWithBaseURL(
                            request.url.toString(),
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        return
                    }
                    super.onReceivedError(view, request, error)
                }
            }

            webChromeClient = object : WebChromeClient() {
                private var customView: View? = null
                private var customViewCallback: CustomViewCallback? = null
                private var fullScreenContainer: FrameLayout? = null

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    config.onProgressChanged(newProgress)
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    handlePermissionRequest(context, request, systemSettings, userSettings)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    origin?.let {
                        handleGeolocationRequest(context, it.trimEnd('/'), callback, systemSettings, userSettings)
                    }
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (customView != null) {
                        callback.onCustomViewHidden()
                        return
                    }

                    val activity = context as? Activity ?: return
                    fullScreenContainer = FrameLayout(activity).apply {
                        addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }

                    customView = view
                    customViewCallback = callback

                    activity.addContentView(
                        fullScreenContainer,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )

                    enterImmersiveMode(activity)

                    visibility = View.GONE
                    fullScreenContainer?.visibility = View.VISIBLE
                }

                override fun onHideCustomView() {
                    val activity = context as? Activity

                    fullScreenContainer?.removeView(customView)
                    fullScreenContainer?.visibility = View.GONE
                    customView = null
                    visibility = View.VISIBLE
                    customViewCallback?.onCustomViewHidden()

                    activity?.let {
                        val shouldExit = !shouldBeImmersed(activity, userSettings)
                        if (shouldExit) {
                            exitImmersiveMode(it)
                        }
                    }
                }
            }

            setOnLongClickListener {
                if (userSettings.allowLinkLongPressContextMenu) {
                    val result = hitTestResult
                    if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                        result.extra?.let { link -> config.onLinkLongClick(link) }
                        true
                    }
                }
                userSettings.allowDefaultLongPress.not()
            }

            setDownloadListener { url, _, _, _, _ ->
                config.showToast(
                    "Downloading files is not yet supported in ${Constants.APP_NAME}."
                )
            }
        }
    }

    return webView
}
