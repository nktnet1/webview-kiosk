package com.nktnet.webview_kiosk.utils

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
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.OverrideUrlLoadingBlockActionOption
import com.nktnet.webview_kiosk.config.option.SslErrorModeOption
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.utils.webview.SchemeType
import com.nktnet.webview_kiosk.utils.webview.getBlockInfo
import com.nktnet.webview_kiosk.utils.webview.scripts.generateDesktopViewportScript
import com.nktnet.webview_kiosk.utils.webview.scripts.generatePrefersColorSchemeOverrideScript
import com.nktnet.webview_kiosk.utils.webview.handlers.handleGeolocationRequest
import com.nktnet.webview_kiosk.utils.webview.handlers.handlePermissionRequest
import com.nktnet.webview_kiosk.utils.webview.handlers.handleSslErrorPromptRequest
import com.nktnet.webview_kiosk.utils.webview.wrapJsInIIFE
import com.nktnet.webview_kiosk.utils.webview.interfaces.BatteryInterface
import com.nktnet.webview_kiosk.utils.webview.interfaces.BrightnessInterface
import com.nktnet.webview_kiosk.utils.webview.isCustomBlockPageUrl
import com.nktnet.webview_kiosk.utils.webview.loadBlockedPage

data class WebViewConfig(
    val systemSettings: SystemSettings,
    val userSettings: UserSettings,
    val blacklistRegexes: List<Regex>,
    val whitelistRegexes: List<Regex>,
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
                useWideViewPort = userSettings.useWideViewport
                loadWithOverviewMode = userSettings.loadWithOverviewMode

                setGeolocationEnabled(userSettings.allowLocation)
                setInitialScale(userSettings.initialScale)
                setSupportZoom(userSettings.supportZoom)

                builtInZoomControls = userSettings.builtInZoomControls
                displayZoomControls = userSettings.displayZoomControls

                allowFileAccess = userSettings.allowLocalFiles
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = userSettings.allowFileAccessFromFileURLs
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = userSettings.allowUniversalAccessFromFileURLs
                mediaPlaybackRequiresUserGesture = userSettings.mediaPlaybackRequiresUserGesture

                mixedContentMode = userSettings.mixedContentMode.mode
                overScrollMode = userSettings.overScrollMode.mode
            }

            if (userSettings.enableBatteryApi) {
                val batteryInterface = BatteryInterface(context)
                addJavascriptInterface(batteryInterface, batteryInterface.name)
            }
            if (userSettings.enableBrightnessApi) {
                val brightnessInterface = BrightnessInterface(context)
                addJavascriptInterface(brightnessInterface, brightnessInterface.name)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    config.setLastErrorUrl("")
                    if (userSettings.requestFocusOnPageStart) {
                        view?.requestFocus()
                    }
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
                            handleExternalSchemeUrl(context, requestUrl)
                        }
                        return true
                    }

                    if (blockCause != null) {
                        when (userSettings.overrideUrlLoadingBlockAction) {
                            OverrideUrlLoadingBlockActionOption.SHOW_BLOCK_PAGE -> {
                                loadBlockedPage(
                                    view,
                                    userSettings,
                                    requestUrl,
                                    blockCause,
                                )
                            }
                            OverrideUrlLoadingBlockActionOption.SHOW_TOAST -> {
                                ToastManager.show(context, userSettings.blockedMessage)
                            }
                            else -> Unit
                        }
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

                    val uri = url.toUri()
                    if (isCustomBlockPageUrl(schemeType, uri)) {
                        // Already on custom block page.
                        val blockUrl = uri.getQueryParameter("url")
                        blockUrl?.let {
                            config.updateAddressBarAndHistory(blockUrl, originalUrl)
                        }
                        return
                    }

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
                ToastManager.show(
                    context,
                    "Downloading files is not supported in ${Constants.APP_NAME}."
                )
            }
        }
    }

    return webView
}
