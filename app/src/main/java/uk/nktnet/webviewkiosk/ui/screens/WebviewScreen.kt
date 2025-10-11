package com.nktnet.webview_kiosk.ui.screens

import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.AddressBarOption
import com.nktnet.webview_kiosk.handlers.BackPressHandler
import com.nktnet.webview_kiosk.handlers.InactivityTimeoutHandler
import com.nktnet.webview_kiosk.handlers.MultitapHandler
import com.nktnet.webview_kiosk.ui.components.webview.AddressBar
import com.nktnet.webview_kiosk.ui.components.webview.FloatingMenuButton
import com.nktnet.webview_kiosk.ui.components.webview.WebviewAwareSwipeRefreshLayout
import com.nktnet.webview_kiosk.ui.components.common.LoadingIndicator
import com.nktnet.webview_kiosk.ui.components.setting.BasicAuthDialog
import com.nktnet.webview_kiosk.ui.components.webview.LinkOptionsDialog
import com.nktnet.webview_kiosk.utils.createCustomWebview
import com.nktnet.webview_kiosk.utils.getMimeType
import com.nktnet.webview_kiosk.utils.isSupportedFileURLMimeType
import com.nktnet.webview_kiosk.utils.rememberLockedState
import com.nktnet.webview_kiosk.utils.tryLockTask
import com.nktnet.webview_kiosk.utils.webview.WebViewNavigation
import com.nktnet.webview_kiosk.utils.webview.generateFileMissingPage
import com.nktnet.webview_kiosk.utils.webview.generateUnsupportedMimeTypePage
import com.nktnet.webview_kiosk.utils.webview.resolveUrlOrSearch
import java.io.File

private enum class TransitionState { TRANSITIONING, PAGE_STARTED, PAGE_FINISHED }

@Composable
fun WebviewScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val isPinned by rememberLockedState()

    var currentUrl by remember { mutableStateOf(systemSettings.currentUrl.takeIf { it.isNotEmpty() } ?: userSettings.homeUrl) }
    var urlBarText by remember { mutableStateOf(TextFieldValue(currentUrl)) }
    var transitionState by remember { mutableStateOf(TransitionState.PAGE_FINISHED) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }

    var linkToOpen by remember { mutableStateOf<String?>(null) }

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

    var toastRef: Toast? = null
    val showToast: (String) -> Unit = { msg ->
        toastRef?.cancel()
        toastRef = Toast.makeText(context, msg, Toast.LENGTH_SHORT).apply { show() }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val webView = createCustomWebview(
        context = context,
        config = com.nktnet.webview_kiosk.utils.WebViewConfig(
            userSettings = userSettings,
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
            },
            onLinkLongClick = { link ->
                linkToOpen = link
            }
        )
    )

    fun customLoadUrl(newUrl: String) {
        transitionState = TransitionState.TRANSITIONING
        val uri = newUrl.toUri()

        // Handle invalid or unrenderable files here.
        // For valid files, delegate to createCustomWebview.
        if (uri.scheme == "file" && userSettings.allowLocalFiles) {
            val mimeType = getMimeType(context, uri)
            val file = File(uri.path ?: "")
            val pageContent = when {
                !file.exists() -> generateFileMissingPage(file, userSettings.theme)
                !isSupportedFileURLMimeType(mimeType) -> generateUnsupportedMimeTypePage(context, file, mimeType, userSettings.theme)
                else -> null
            }
            pageContent?.let {
                webView.loadDataWithBaseURL(newUrl, it, "text/html", "UTF-8", null)
                return
            }
        }
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
            .focusRequester(focusRequester)
            .focusable()
            .windowInsetsPadding(userSettings.webViewInset.toWindowInsets()))
    {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showAddressBar) {
                AddressBar(
                    urlBarText = urlBarText,
                    onUrlBarTextChange = { urlBarText = it },
                    hasFocus = hasFocus,
                    onFocusChanged = { focusState -> hasFocus = focusState.isFocused },
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

                        fun initWebviewApply(initialUrl: String) = webView.apply {
                            customLoadUrl(initialUrl)
                        }

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
                    onLockClick = {
                        tryLockTask(activity, showToast)
                    },
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

    LinkOptionsDialog(
        link = linkToOpen,
        onDismiss = { linkToOpen = null },
        onOpenLink = { url -> customLoadUrl(url) },
    )

}
