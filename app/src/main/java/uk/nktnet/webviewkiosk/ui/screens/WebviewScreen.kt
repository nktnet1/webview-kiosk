package uk.nktnet.webviewkiosk.ui.screens

import android.app.Activity
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
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
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.AddressBarOption
import uk.nktnet.webviewkiosk.handlers.BackPressHandler
import uk.nktnet.webviewkiosk.handlers.InactivityTimeoutHandler
import uk.nktnet.webviewkiosk.handlers.MultitapHandler
import uk.nktnet.webviewkiosk.ui.components.webview.AddressBar
import uk.nktnet.webviewkiosk.ui.components.webview.FloatingMenuButton
import uk.nktnet.webviewkiosk.ui.components.webview.WebviewAwareSwipeRefreshLayout
import uk.nktnet.webviewkiosk.ui.components.common.LoadingIndicator
import uk.nktnet.webviewkiosk.ui.components.setting.BasicAuthDialog
import uk.nktnet.webviewkiosk.utils.createCustomWebview
import uk.nktnet.webviewkiosk.utils.rememberLockedState
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation
import uk.nktnet.webviewkiosk.utils.webview.resolveUrlOrSearch

private enum class TransitionState { TRANSITIONING, PAGE_STARTED, PAGE_FINISHED }

@Composable
fun WebviewScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val isPinned by rememberLockedState()

    var currentUrl by remember { mutableStateOf(systemSettings.currentUrl.takeIf { it.isNotEmpty() } ?: userSettings.homeUrl) }
    var urlBarText by remember { mutableStateOf(TextFieldValue(currentUrl)) }
    var transitionState by remember { mutableStateOf(TransitionState.PAGE_FINISHED) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }

    val showAddressBar = when (userSettings.addressBarMode) {
        AddressBarOption.SHOWN -> true
        AddressBarOption.HIDDEN -> false
        AddressBarOption.HIDDEN_WHEN_LOCKED -> !isPinned
    }

    val focusRequester = remember { FocusRequester() }
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var authHandler by remember { mutableStateOf<HttpAuthHandler?>(null) }
    var authHost by remember { mutableStateOf<String?>(null) }
    var authRealm by remember { mutableStateOf<String?>(null) }

    val webView = createCustomWebview(
        context = context,
        config = uk.nktnet.webviewkiosk.utils.WebViewConfig(
            userSettings = userSettings,
            theme = userSettings.theme,
            onPageStarted = { transitionState = TransitionState.PAGE_STARTED },
            onPageFinished = { url ->
                transitionState = TransitionState.PAGE_FINISHED
                isRefreshing = false
            },
            doUpdateVisitedHistory = { url ->
                if (!hasFocus) urlBarText = urlBarText.copy(text = url)
                currentUrl = url
                WebViewNavigation.appendWebviewHistory(systemSettings, url)
            },
            onHttpAuthRequest = { handler, host, realm ->
                authHandler = handler
                authHost = host
                authRealm = realm
            }
        )
    )

    fun customLoadUrl(newUrl: String) {
        transitionState = TransitionState.TRANSITIONING
        webView.loadUrl(newUrl)
    }

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(userSettings.acceptCookies)
    cookieManager.setAcceptThirdPartyCookies(webView, userSettings.acceptThirdPartyCookies)
    BackPressHandler(webView, ::customLoadUrl, onBackPressedDispatcher)

    val addressBarSearch: (String) -> Unit = { input ->
        val searchUrl = resolveUrlOrSearch(userSettings.searchProviderUrl, input.trim())
        if (searchUrl.isNotBlank() && (searchUrl != currentUrl || userSettings.allowRefresh)) {
            webView.requestFocus()
            customLoadUrl(searchUrl)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(userSettings.webViewInset.toWindowInsets()))
    {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showAddressBar) {
                AddressBar(
                    urlBarText = urlBarText,
                    onUrlBarTextChange = { urlBarText = it },
                    hasFocus = hasFocus,
                    onFocusChanged = { focusState -> hasFocus = focusState.isFocused },
                    focusRequester = focusRequester,
                    addressBarSearch = addressBarSearch,
                    webView = webView,
                    customLoadUrl = ::customLoadUrl,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        var initialUrl = currentUrl

                        if (systemSettings.intentUrl.isNotEmpty()) {
                            initialUrl = systemSettings.intentUrl
                            systemSettings.intentUrl = ""
                        } else if (systemSettings.isFreshLaunch) {
                            systemSettings.isFreshLaunch = false
                            if (userSettings.resetOnLaunch) {
                                initialUrl = userSettings.homeUrl
                                systemSettings.clearHistory()
                            }
                        }

                        urlBarText = urlBarText.copy(text = initialUrl)

                        fun initWebviewApply(initialUrl: String) = webView.apply { customLoadUrl(initialUrl) }

                        if (userSettings.allowRefresh) {
                            WebviewAwareSwipeRefreshLayout(ctx, webView).apply {
                                setOnRefreshListener {
                                    isRefreshing = true
                                    webView.reload()
                                }
                                addView(initWebviewApply(initialUrl))
                            }
                        } else {
                            initWebviewApply(initialUrl)
                        }
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
                    onHomeClick = { WebViewNavigation.goHome(::customLoadUrl, systemSettings, userSettings) },
                    onLockClick = { try { activity?.startLockTask() } catch (_: Exception) {} },
                    navController = navController
                )
            }
        }
    }

    if (userSettings.resetOnInactivitySeconds >= Constants.MIN_INACTIVITY_TIMEOUT_SECONDS) {
        InactivityTimeoutHandler(systemSettings, userSettings, ::customLoadUrl)
    }
    if (userSettings.allowGoHome) {
        MultitapHandler { WebViewNavigation.goHome(::customLoadUrl, systemSettings, userSettings) }
    }
    BasicAuthDialog(authHandler, authHost, authRealm) { authHandler = null }
}
