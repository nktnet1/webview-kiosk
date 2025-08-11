package com.nktnet.webview_kiosk.ui.view

import android.app.Activity
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
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
import com.nktnet.webview_kiosk.utils.resolveUrlOrSearch

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
            }
        }

        if (!isPinned) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                FloatingMenuButton(
                    onHomeClick = { triggerLoad(userSettings.homeUrl) },
                    onLockClick = {
                        try {
                            activity?.startLockTask()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    navController = navController,
                )
            }
        }
    }


    MultitapHandler(
        onSuccess = {
            triggerLoad(userSettings.homeUrl)
        }
    )
}

@Composable
private fun MultitapHandler(
    requiredTaps: Int = 10,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var tapsLeft by remember { mutableIntStateOf(requiredTaps) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val maxInterval = 500L

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    val userSettings = remember { UserSettings(context) }
    var allowGoHome by remember { mutableStateOf(userSettings.allowGoHome ) }

    if (allowGoHome) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .pointerInteropFilter { motionEvent ->
                    if (motionEvent.action == android.view.MotionEvent.ACTION_DOWN) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime > maxInterval) {
                            tapsLeft = requiredTaps
                        }
                        tapsLeft = 0.coerceAtLeast(tapsLeft - 1)
                        lastTapTime = now
                        when {
                            tapsLeft <= 0 -> {
                                tapsLeft = requiredTaps
                                showToast("Navigating home")
                                onSuccess()
                            }

                            tapsLeft <= 5 -> {
                                showToast("Tap $tapsLeft more times navigate home")
                            }
                        }
                    }
                    false
                }
        )
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
