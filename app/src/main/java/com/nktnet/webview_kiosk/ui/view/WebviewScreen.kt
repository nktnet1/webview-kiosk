package com.nktnet.webview_kiosk.ui.view

import android.app.Activity
import android.webkit.CookieManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.nktnet.webview_kiosk.config.option.AddressBarOption
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.handlers.BackPressHandler
import com.nktnet.webview_kiosk.handlers.MultitapHandler
import com.nktnet.webview_kiosk.ui.components.AddressBar
import com.nktnet.webview_kiosk.ui.components.FloatingMenuButton
import com.nktnet.webview_kiosk.ui.components.common.LoadingIndicator
import com.nktnet.webview_kiosk.utils.createCustomWebview
import com.nktnet.webview_kiosk.utils.rememberLockedState
import com.nktnet.webview_kiosk.utils.webview.WebViewNavigation
import com.nktnet.webview_kiosk.utils.webview.resolveUrlOrSearch

private enum class TransitionState { TRANSITIONING, PAGE_STARTED, PAGE_FINISHED }
@Composable
fun WebviewScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val isPinned by rememberLockedState()

    var currentUrl by remember {
        mutableStateOf(
            systemSettings.currentUrl.takeIf { it.isNotEmpty() }
            ?: userSettings.homeUrl
        )
    }
    var urlBarText by remember { mutableStateOf(TextFieldValue(currentUrl)) }
    var transitionState by remember { mutableStateOf(TransitionState.PAGE_FINISHED) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }

    val blacklistRegexes = remember(userSettings.websiteBlacklist) {
        userSettings.websiteBlacklist.lines()
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }
    }

    val whitelistRegexes = remember(userSettings.websiteWhitelist) {
        userSettings.websiteWhitelist.lines()
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }
    }

    val showAddressBar = when (userSettings.addressBarMode) {
        AddressBarOption.SHOWN -> true
        AddressBarOption.HIDDEN -> false
        AddressBarOption.HIDDEN_WHEN_LOCKED -> !isPinned
    }

    val focusRequester = remember { FocusRequester() }
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val webView = createCustomWebview(
        context = context,
        theme = userSettings.theme,
        blockedMessage = userSettings.blockedMessage,
        blacklistRegexes = blacklistRegexes,
        whitelistRegexes = whitelistRegexes,
        allowOtherUrlSchemes = userSettings.allowOtherUrlSchemes,
        enableJavaScript = userSettings.enableJavaScript,
        enableDomStorage = userSettings.enableDomStorage,
        cacheMode = userSettings.cacheMode,
        onPageStarted = { transitionState = TransitionState.PAGE_STARTED },
        onPageFinished = { url ->
            transitionState = TransitionState.PAGE_FINISHED
            isRefreshing = false
        },
        doUpdateVisitedHistory = { url ->
            if (!hasFocus) {
                urlBarText = urlBarText.copy(text = url)
            }
            currentUrl = url
            WebViewNavigation.appendWebviewHistory(systemSettings, url)
        }
    )

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(userSettings.acceptCookies)
    cookieManager.setAcceptThirdPartyCookies(webView, userSettings.acceptThirdPartyCookies)

    BackPressHandler(webView, onBackPressedDispatcher)

    val addressBarSearch: (String) -> Unit = { input ->
        val searchUrl = resolveUrlOrSearch(userSettings.searchProviderUrl, input.trim())
        if (searchUrl.isNotBlank() && (searchUrl != currentUrl || userSettings.allowRefresh)) {
            transitionState = TransitionState.TRANSITIONING
            webView.requestFocus()
            webView.loadUrl(searchUrl)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(userSettings.webViewInset.toWindowInsets())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showAddressBar) {
                AddressBar(
                    urlBarText = urlBarText,
                    onUrlBarTextChange = { urlBarText = it },
                    hasFocus = hasFocus,
                    onFocusChanged = { focusState -> hasFocus = focusState.isFocused },
                    focusRequester = focusRequester,
                    addressBarSearch = addressBarSearch,
                    webView = webView
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        val initialUrl = if (systemSettings.intentUrl.isNotEmpty()) {
                            systemSettings.intentUrl.also { systemSettings.intentUrl = "" }
                        } else {
                            currentUrl
                        }
                        transitionState = TransitionState.TRANSITIONING
                        urlBarText = urlBarText.copy(text = initialUrl)
                        if (userSettings.allowRefresh) {
                            SwipeRefreshLayout(ctx).apply {
                                setOnRefreshListener { isRefreshing = true; webView.reload() }
                                addView(webView.apply {
                                    loadUrl(initialUrl)
                                })
                            }
                        } else {
                            webView.apply {
                                loadUrl(initialUrl)
                            }
                        }
                    },
                    update = { view ->
                        if (view is SwipeRefreshLayout) view.isRefreshing = isRefreshing
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (transitionState == TransitionState.TRANSITIONING) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x88000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator("Loading...")
                    }
                }
            }
        }

        if (!isPinned) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                FloatingMenuButton(
                    onHomeClick = { WebViewNavigation.goHome(webView, systemSettings, userSettings) },
                    onLockClick = { try { activity?.startLockTask() } catch (_: Exception) {} },
                    navController = navController,
                )
            }
        }
    }

    MultitapHandler { WebViewNavigation.goHome(webView, systemSettings, userSettings) }
}
