package com.nktnet.webview_kiosk.utils

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
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.utils.webview.BlockCause
import com.nktnet.webview_kiosk.utils.webview.generateBlockedPageHtml
import com.nktnet.webview_kiosk.utils.webview.generateDesktopViewportScript
import com.nktnet.webview_kiosk.utils.webview.generatePrefersColorSchemeOverrideScript
import com.nktnet.webview_kiosk.utils.webview.handleExternalScheme
import com.nktnet.webview_kiosk.utils.webview.isBlockedUrl
import com.nktnet.webview_kiosk.utils.webview.wrapJsInIIFE

data class WebViewConfig(
    val userSettings: UserSettings,
    val showToast: (message: String) -> Unit,
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
                        evaluateJavascript(generatePrefersColorSchemeOverrideScript(userSettings.theme), null)
                    }
                    if (userSettings.customScriptOnPageStart.isNotBlank()) {
                        view?.evaluateJavascript(wrapJsInIIFE(userSettings.customScriptOnPageStart), null)
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

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    url?.let {
                        if (!isShowingBlockedPage) {
                            if (isBlocked(it)) {
                                loadBlockedPage(url,BlockCause.BLACKLIST)
                                return
                            }
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
                private var customView: View? = null
                private var customViewCallback: CustomViewCallback? = null
                private var fullScreenContainer: FrameLayout? = null

                override fun onPermissionRequest(request: PermissionRequest) {
                    val grantedResources = mutableListOf<String>()
                    request.resources.forEach { res ->
                        when (res) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> if (userSettings.allowCamera) grantedResources.add(res)
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> if (userSettings.allowMicrophone) grantedResources.add(res)
                        }
                    }
                    if (grantedResources.isNotEmpty()) request.grant(grantedResources.toTypedArray()) else request.deny()
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, userSettings.allowLocation, false)
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

            setDownloadListener { url, _, _, _, _ ->
                config.showToast(
                    "Downloading files is not yet supported in ${Constants.APP_NAME}."
                )
            }
        }
    }

    return webView
}
