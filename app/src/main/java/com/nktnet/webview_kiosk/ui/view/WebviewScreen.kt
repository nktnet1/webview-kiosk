package com.nktnet.webview_kiosk.ui.view

import android.app.Activity
import android.net.Uri
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
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
import com.nktnet.webview_kiosk.ui.components.AddressBar
import com.nktnet.webview_kiosk.ui.components.FloatingMenuButton
import com.nktnet.webview_kiosk.ui.components.common.LoadingIndicator
import com.nktnet.webview_kiosk.utils.createCustomWebview
import com.nktnet.webview_kiosk.utils.customLoadUrl
import com.nktnet.webview_kiosk.utils.rememberLockedState

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
        onPageStarted = { transitionState = TransitionState.PAGE_STARTED },
        onPageFinished = { url ->
            if (!hasFocus) {
                urlBarText = urlBarText.copy(text = url)
            }
            currentUrl = url
            systemSettings.lastUrl = url
            transitionState = TransitionState.PAGE_FINISHED
            isRefreshing = false
        }
    )

    fun WebView.customLoadUrlWithDefaults(url: String) =
        customLoadUrl(url, blacklistRegexes, whitelistRegexes, blockedMessage)

    HandleBackPress(webView, onBackPressedDispatcher, userSettings.allowBackwardsNavigation)

    val triggerLoad: (String) -> Unit = { input ->
        val searchUrl = resolveUrlOrSearch(searchProviderUrl, input.trim())
        if (searchUrl.isNotBlank() && searchUrl != currentUrl || allowRefresh) {
            transitionState = TransitionState.TRANSITIONING
            webView.requestFocus()
            currentUrl = searchUrl
            webView.customLoadUrlWithDefaults(searchUrl)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (showAddressBar) {
            AddressBar(
                urlBarText = urlBarText,
                onUrlBarTextChange = { urlBarText = it },
                hasFocus = hasFocus,
                onFocusChanged = { focusState -> hasFocus = focusState.isFocused },
                focusRequester = focusRequester,
                triggerLoad = triggerLoad,
                webView = webView
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    if (userSettings.allowRefresh) {
                        SwipeRefreshLayout(ctx).apply {
                            setOnRefreshListener {
                                isRefreshing = true
                                webView.reload()
                            }
                            addView(webView.apply { customLoadUrlWithDefaults(currentUrl) })
                        }
                    } else {
                        webView.apply { customLoadUrlWithDefaults(currentUrl) }
                    }
                },
                update = { view ->
                    if (view is SwipeRefreshLayout) {
                        view.isRefreshing = isRefreshing
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (transitionState == TransitionState.TRANSITIONING) {
                Box(
                    Modifier.fillMaxSize().background(Color(0x88000000)),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator("Loading...")
                }
            }

            if (!isPinned) {
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    FloatingMenuButton(
                        onHomeClick = { webView.customLoadUrlWithDefaults(userSettings.homeUrl) },
                        onLockClick = {
                            try {
                                activity?.startLockTask()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        navController = navController
                    )
                }
            }
        }
    }
}

private enum class TransitionState {
    TRANSITIONING,
    PAGE_STARTED,
    PAGE_FINISHED
}

@Composable
private fun HandleBackPress(
    webView: WebView,
    dispatcher: OnBackPressedDispatcher?,
    allowBackwardsNavigation: Boolean
) {
    DisposableEffect(webView, dispatcher, allowBackwardsNavigation) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (allowBackwardsNavigation && webView.canGoBack()) {
                    webView.goBack()
                }
            }
        }
        dispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }
}

private fun resolveUrlOrSearch(searchProviderUrl: String, input: String): String {
    return when {
        URLUtil.isValidUrl(input) -> input
        input.contains('.') -> "https://$input"
        else -> "${searchProviderUrl}${Uri.encode(input)}"
    }
}
