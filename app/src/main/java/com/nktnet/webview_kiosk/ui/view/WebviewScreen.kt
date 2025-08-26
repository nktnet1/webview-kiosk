package com.nktnet.webview_kiosk.ui.view

import android.app.Activity
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

@Composable
fun WebviewScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val isPinned by rememberLockedState()

    var currentUrl by remember { mutableStateOf(systemSettings.lastUrl.ifEmpty { userSettings.homeUrl }) }
    var blockedMessage by remember { mutableStateOf(userSettings.blockedMessage) }
    var allowRefresh by remember { mutableStateOf(userSettings.allowRefresh) }
    var allowOtherUrlSchemes by remember { mutableStateOf(userSettings.allowOtherUrlSchemes ) }

    var searchProviderUrl by remember { mutableStateOf(userSettings.searchProviderUrl) }
    var theme by remember { mutableStateOf(userSettings.theme) }

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

    var urlBarText by remember { mutableStateOf(TextFieldValue(currentUrl)) }
    var transitionState by remember { mutableStateOf(TransitionState.PAGE_FINISHED) }
    var isRefreshing by remember { mutableStateOf(false) }

    val showAddressBar = when (userSettings.addressBarMode) {
        AddressBarOption.SHOWN -> true
        AddressBarOption.HIDDEN -> false
        AddressBarOption.HIDDEN_WHEN_LOCKED -> !isPinned
    }

    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val webView = createCustomWebview(
        context = context,
        theme = theme,
        blockedMessage = blockedMessage,
        blacklistRegexes = blacklistRegexes,
        whitelistRegexes = whitelistRegexes,
        allowOtherUrlSchemes = allowOtherUrlSchemes,
        onPageStarted = { transitionState = TransitionState.PAGE_STARTED },
        onPageFinished = { url ->
            currentUrl = url
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

    BackPressHandler(webView, onBackPressedDispatcher)

    val addressBarSearch: (String) -> Unit = { input ->
        val searchUrl = resolveUrlOrSearch(searchProviderUrl, input.trim())
        if (searchUrl.isNotBlank() && (searchUrl != currentUrl || allowRefresh)) {
            transitionState = TransitionState.TRANSITIONING
            webView.requestFocus()
            webView.loadUrl(searchUrl)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        if (userSettings.allowRefresh) {
                            SwipeRefreshLayout(ctx).apply {
                                setOnRefreshListener { isRefreshing = true; webView.reload() }
                                addView(webView.apply { loadUrl(currentUrl) })
                            }
                        } else webView.apply { loadUrl(currentUrl) }
                    },
                    update = { view -> if (view is SwipeRefreshLayout) view.isRefreshing = isRefreshing },
                    modifier = Modifier.fillMaxSize()
                )

                if (transitionState == TransitionState.TRANSITIONING) {
                    Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
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

private enum class TransitionState { TRANSITIONING, PAGE_STARTED, PAGE_FINISHED }
