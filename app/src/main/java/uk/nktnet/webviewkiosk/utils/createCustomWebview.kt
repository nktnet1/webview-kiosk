package uk.nktnet.webviewkiosk.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.SslErrorModeOption
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.utils.webview.handlers.handleExternalScheme
import uk.nktnet.webviewkiosk.utils.webview.html.BlockCause
import uk.nktnet.webviewkiosk.utils.webview.html.generateBlockedPageHtml
import uk.nktnet.webviewkiosk.utils.webview.scripts.generateDesktopViewportScript
import uk.nktnet.webviewkiosk.utils.webview.scripts.generatePrefersColorSchemeOverrideScript
import uk.nktnet.webviewkiosk.utils.webview.scripts.generateBatterySetupScript
import uk.nktnet.webviewkiosk.utils.webview.handlers.handleGeolocationRequest
import uk.nktnet.webviewkiosk.utils.webview.handlers.handlePermissionRequest
import uk.nktnet.webviewkiosk.utils.webview.handlers.handleSslErrorPromptRequest
import uk.nktnet.webviewkiosk.utils.webview.isBlockedUrl
import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE
import uk.nktnet.webviewkiosk.utils.webview.BatteryInterface
import java.net.URLEncoder

data class WebViewConfig(
    val systemSettings: SystemSettings,
    val userSettings: UserSettings,
    val blacklistRegexes: List<Regex>,
    val whitelistRegexes: List<Regex>,
    val showToast: (message: String) -> Unit,
    val setLastErrorUrl: (errorUrl: String) -> Unit,
    val finishSwipeRefresh: () -> Unit,
    val onProgressChanged: (newProgress: Int) -> Unit,
    val updateAddressBarAndHistory: (url: String, originalUrl: String?) -> Unit,
    val onHttpAuthRequest: (handler: HttpAuthHandler?, host: String?, realm: String?) -> Unit,
    val onLinkLongClick: (url: String) -> Unit
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun createCustomWebview(
    context: Context,
    config: WebViewConfig
): WebView {
    val systemSettings = config.systemSettings
    val userSettings = config.userSettings

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

            // Add JavaScript interface for battery status if enabled
            if (userSettings.enableBatteryApi) {
                addJavascriptInterface(BatteryInterface(context), "AndroidBattery")
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    config.setLastErrorUrl("")
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
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    config.finishSwipeRefresh()

                    url?.let {
                        // [URL_BEFORE_NAVIGATION] reset when loaded - must check
                        // progress = 100 due to webview bug where onPageFinished
                        // gets called multiple times.
                        // https://issuetracker.google.com/issues/36983315
                        if (progress == 100) {
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
                            if (userSettings.enableBatteryApi) {
                                view?.evaluateJavascript(generateBatterySetupScript(), null)
                            }
                            systemSettings.urlBeforeNavigation = ""
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val requestUrl = request?.url.toString()
                    if (requestUrl.isEmpty()) {
                        return false
                    }
                    systemSettings.urlBeingHandled = requestUrl
                    if (systemSettings.urlBeforeNavigation.isEmpty()) {
                        // [URL_BEFORE_NAVIGATION] first to run for native navigation (non-SPA)
                        systemSettings.urlBeforeNavigation = systemSettings.currentUrl
                    }

                    val (schemeType, blockCause) = getBlockInfo(
                        url = requestUrl,
                        blacklistRegexes = config.blacklistRegexes,
                        whitelistRegexes = config.whitelistRegexes,
                        userSettings = userSettings
                    )
                    val uri = requestUrl.toUri()
                    if (schemeType == SchemeType.WEBVIEW_KIOSK && uri.host == "block") {
                        val blockUrl = uri.getQueryParameter("url")
                        if (blockUrl != null) {
                            loadUrl(blockUrl)
                            return true
                        }
                    } else if (schemeType == SchemeType.OTHER) {
                        if (userSettings.allowOtherUrlSchemes) {
                            handleExternalScheme(context, requestUrl)
                        }
                        return true
                    }

                    if (blockCause != null) {
                        loadBlockedPage(
                            view,
                            userSettings,
                            requestUrl,
                            blockCause,
                        )
                        return true
                    }
                    return false
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean
                ) {
                    if (url == null) {
                        return
                    }
                    if (
                        systemSettings.urlBeingHandled.trimEnd('/') == url.trimEnd('/')
                    ) {
                        config.updateAddressBarAndHistory(url, originalUrl)
                        return
                    }

                    /**
                     * This section of the code is only ever reached if either customLoadUrl or
                     * shouldOverrideUrlLoading was not triggered, e.g. during JS navigation in
                     * Single Page Applications (e.g. a React SPA).
                     */
                    if (systemSettings.urlBeforeNavigation.isEmpty()) {
                        systemSettings.urlBeforeNavigation = systemSettings.currentUrl
                    }

                    systemSettings.urlBeingHandled = url

                    val (schemeType, blockCause) = getBlockInfo(
                        url = url,
                        blacklistRegexes = config.blacklistRegexes,
                        whitelistRegexes = config.whitelistRegexes,
                        userSettings = userSettings
                    )
                    if (blockCause != null) {
                        loadBlockedPage(
                            view,
                            userSettings,
                            url,
                            blockCause,
                        )
                        config.updateAddressBarAndHistory(url, originalUrl)
                        return
                    }
                    if (schemeType == SchemeType.OTHER) {
                        return
                    }
                    config.updateAddressBarAndHistory(url, originalUrl)
                }

                override fun onReceivedHttpAuthRequest(
                    view: WebView?,
                    handler: HttpAuthHandler?,
                    host: String?,
                    realm: String?
                ) {
                    config.onHttpAuthRequest(handler, host, realm)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && request?.isForMainFrame == true
                    ) {
                        config.setLastErrorUrl(request.url.toString())
                        return
                    }
                    super.onReceivedError(view, request, error)
                }

                @Deprecated("For API < 23")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && !failingUrl.isNullOrEmpty()) {
                        config.setLastErrorUrl(failingUrl)
                    }
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    when (userSettings.sslErrorMode) {
                        SslErrorModeOption.BLOCK -> handler?.cancel()
                        SslErrorModeOption.PROMPT -> handleSslErrorPromptRequest(
                            context, handler, error
                        )
                        SslErrorModeOption.PROCEED -> handler?.proceed()
                    }
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
                        return@setOnLongClickListener true
                    }
                }
                userSettings.allowDefaultLongPress.not()
            }

            setDownloadListener { _, _, _, _, _ ->
                config.showToast(
                    "Downloading files is not yet supported in ${Constants.APP_NAME}."
                )
            }
        }
    }

    return webView
}

enum class SchemeType {
    FILE,
    WEB,
    DATA,
    WEBVIEW_KIOSK,
    OTHER
}

fun getBlockInfo(
    url: String,
    blacklistRegexes: List<Regex>,
    whitelistRegexes: List<Regex>,
    userSettings: UserSettings
): Pair<SchemeType, BlockCause?> {
    val uri = url.toUri()
    val scheme = uri.scheme?.lowercase() ?: ""
    val schemeType = when (scheme) {
        "file" -> SchemeType.FILE
        "http", "https" -> SchemeType.WEB
        "data" -> SchemeType.DATA
        "webviewkiosk" -> SchemeType.WEBVIEW_KIOSK
        else -> SchemeType.OTHER
    }

    val blockCause = when {
        isBlockedUrl(url, blacklistRegexes, whitelistRegexes) -> BlockCause.BLACKLIST
        schemeType == SchemeType.FILE && !userSettings.allowLocalFiles -> BlockCause.LOCAL_FILE
        else -> null
    }
    return schemeType to blockCause
}

fun loadBlockedPage(
    webView: WebView?,
    userSettings: UserSettings,
    url: String,
    blockCause: BlockCause,
) {
    val baseUrl = if ( url.toUri().scheme !in setOf("http", "https", "file")) {
        "webviewkiosk://block?cause=${blockCause.name}&url=${URLEncoder.encode(url, "UTF-8")}"
    } else {
        url
    }

    webView?.loadDataWithBaseURL(
        baseUrl,
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
}
