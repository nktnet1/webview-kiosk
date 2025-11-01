package uk.nktnet.webviewkiosk.ui.screens

import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.URLUtil.isValidUrl
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.AddressBarOption
import uk.nktnet.webviewkiosk.config.option.BackButtonHoldActionOption
import uk.nktnet.webviewkiosk.config.option.KioskControlPanelRegionOption
import uk.nktnet.webviewkiosk.config.option.SearchSuggestionEngineOption
import uk.nktnet.webviewkiosk.handlers.backbutton.BackPressHandler
import uk.nktnet.webviewkiosk.handlers.InactivityTimeoutHandler
import uk.nktnet.webviewkiosk.handlers.KioskControlPanel
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.ui.components.webview.AddressBar
import uk.nktnet.webviewkiosk.ui.components.webview.FloatingMenuButton
import uk.nktnet.webviewkiosk.ui.components.webview.WebviewAwareSwipeRefreshLayout
import uk.nktnet.webviewkiosk.ui.components.setting.BasicAuthDialog
import uk.nktnet.webviewkiosk.ui.components.webview.AddressBarSearchSuggestions
import uk.nktnet.webviewkiosk.ui.components.webview.LinkOptionsDialog
import uk.nktnet.webviewkiosk.utils.SchemeType
import uk.nktnet.webviewkiosk.utils.createCustomWebview
import uk.nktnet.webviewkiosk.utils.enterImmersiveMode
import uk.nktnet.webviewkiosk.utils.exitImmersiveMode
import uk.nktnet.webviewkiosk.utils.getBlockInfo
import uk.nktnet.webviewkiosk.utils.getMimeType
import uk.nktnet.webviewkiosk.utils.isSupportedFileURLMimeType
import uk.nktnet.webviewkiosk.utils.loadBlockedPage
import uk.nktnet.webviewkiosk.utils.shouldBeImmersed
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.webview.SearchSuggestionEngine
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation
import uk.nktnet.webviewkiosk.utils.webview.html.generateFileMissingPage
import uk.nktnet.webviewkiosk.utils.webview.html.generateUnsupportedMimeTypePage
import uk.nktnet.webviewkiosk.utils.webview.resolveUrlOrSearch
import java.io.File

@Composable
fun WebviewScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val focusManager = LocalFocusManager.current

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val isLocked by LockStateSingleton.isLocked

    val lastVisitedUrl = systemSettings.currentUrl.takeIf { it.isNotEmpty() } ?: userSettings.homeUrl
    var urlBarText by remember {
        mutableStateOf(
            TextFieldValue(
                lastVisitedUrl
            )
        )
    }

    var isSwipeRefreshing by remember { mutableStateOf(false) }
    var addressBarHasFocus by remember { mutableStateOf(false) }

    var linkToOpen by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }

    val showAddressBar = when (userSettings.addressBarMode) {
        AddressBarOption.SHOWN -> true
        AddressBarOption.HIDDEN -> false
        AddressBarOption.HIDDEN_WHEN_LOCKED -> !isLocked
    }

    var authHandler by remember { mutableStateOf<HttpAuthHandler?>(null) }
    var authHost by remember { mutableStateOf<String?>(null) }
    var authRealm by remember { mutableStateOf<String?>(null) }

    var toastRef: Toast? = null
    val showToast: (String) -> Unit = { msg ->
        toastRef?.cancel()
        toastRef = Toast.makeText(context, msg, Toast.LENGTH_SHORT).apply {
            show()
        }
    }

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

    var lastErrorUrl by remember { mutableStateOf("") }

    var suggestions by remember { mutableStateOf(listOf<String>()) }

    if (userSettings.searchSuggestionEngine != SearchSuggestionEngineOption.NONE) {
        LaunchedEffect(addressBarHasFocus, urlBarText.text) {
            if (
                addressBarHasFocus
                && urlBarText.text.isNotBlank()
                && !isValidUrl(urlBarText.text)
            ) {
                delay(300)
                suggestions = try {
                    withContext(Dispatchers.IO) {
                        SearchSuggestionEngine.suggest(
                            userSettings.searchSuggestionEngine,
                            urlBarText.text
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                suggestions = emptyList()
            }
        }
    }

    DisposableEffect( activity, isLocked) {
        if (activity != null) {
            val shouldImmerse = shouldBeImmersed(activity, userSettings)
            if (shouldImmerse) {
                enterImmersiveMode(activity)
            } else {
                exitImmersiveMode(activity)
            }
        }
        onDispose {
            activity?.let { exitImmersiveMode(it) }
        }
    }

    fun updateAddressBarAndHistory(url: String, originalUrl: String?) {
        if (!addressBarHasFocus) {
            urlBarText = urlBarText.copy(text = url)
        }
        WebViewNavigation.appendWebviewHistory(
            systemSettings,
            url,
            originalUrl,
            userSettings.replaceHistoryUrlOnRedirect
        )
    }

    val webView = createCustomWebview(
        context = context,
        config = uk.nktnet.webviewkiosk.utils.WebViewConfig(
            systemSettings = systemSettings,
            userSettings = userSettings,
            blacklistRegexes = blacklistRegexes,
            whitelistRegexes = whitelistRegexes,
            showToast = showToast,
            setLastErrorUrl = { errorUrl ->
                lastErrorUrl = errorUrl
            },
            onProgressChanged = { newProgress -> progress = newProgress },
            finishSwipeRefresh = {
                isSwipeRefreshing = false
            },
            updateAddressBarAndHistory = ::updateAddressBarAndHistory,
            onHttpAuthRequest = { handler, host, realm ->
                authHandler = handler
                authHost = host
                authRealm = realm
            },
            onLinkLongClick = { link ->
                linkToOpen = link
            },
        )
    )

    fun customLoadUrl(newUrl: String) {
        systemSettings.urlBeingHandled = newUrl
        val (schemeType, blockCause) = getBlockInfo(
            url = newUrl,
            blacklistRegexes = blacklistRegexes,
            whitelistRegexes = whitelistRegexes,
            userSettings = userSettings
        )
        if (blockCause != null) {
            loadBlockedPage(
                webView,
                userSettings,
                newUrl,
                blockCause,
            )
            return
        }
        if (schemeType == SchemeType.FILE) {
            val uri = newUrl.toUri()
            val mimeType = getMimeType(context, uri)
            val file = File(uri.path ?: "")
            val pageContent = when {
                !file.exists() -> generateFileMissingPage(file, userSettings.theme)
                !isSupportedFileURLMimeType(mimeType) -> generateUnsupportedMimeTypePage(
                    context, file, mimeType, userSettings.theme
                )
                else -> null
            }
            pageContent?.let {
                webView.loadDataWithBaseURL(
                    newUrl,
                    it,
                    "text/html",
                    "UTF-8",
                    null
                )
                return
            }
        }
        webView.loadUrl(newUrl)
    }

    if (
        userSettings.refreshOnLoadingErrorIntervalSeconds
        >= Constants.MIN_REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS
    ) {
        LaunchedEffect(lastErrorUrl) {
            while (lastErrorUrl.isNotEmpty()) {
                delay(userSettings.refreshOnLoadingErrorIntervalSeconds * 1000L)
                WebViewNavigation.refresh(
                    ::customLoadUrl, systemSettings, userSettings
                )
            }
        }
    }

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(userSettings.acceptCookies)
    cookieManager.setAcceptThirdPartyCookies(
        webView, userSettings.acceptThirdPartyCookies
    )

    val addressBarSearch: (String) -> Unit = { input ->
        val searchUrl = resolveUrlOrSearch(
            userSettings.searchProviderUrl, input.trim()
        )
        focusManager.clearFocus()
        if (searchUrl.isNotBlank() && (searchUrl != systemSettings.currentUrl || userSettings.allowRefresh)) {
            webView.requestFocus()
            customLoadUrl(searchUrl)
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
                    hasFocus = addressBarHasFocus,
                    onFocusChanged = { focusState -> addressBarHasFocus = focusState.isFocused },
                    addressBarSearch = addressBarSearch,
                    customLoadUrl = ::customLoadUrl,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        var initialUrl = lastVisitedUrl

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

                        if (userSettings.allowRefresh && userSettings.allowPullToRefresh) {
                            WebviewAwareSwipeRefreshLayout(ctx, webView).apply {
                                setOnRefreshListener {
                                    isSwipeRefreshing = true
                                    WebViewNavigation.refresh(
                                        ::customLoadUrl,
                                        systemSettings,
                                        userSettings
                                    )
                                }
                                addView(initWebviewApply(initialUrl))
                            }
                        } else {
                            initWebviewApply(initialUrl)
                        }
                    },
                    update = { view ->
                        if (view is SwipeRefreshLayout) {
                            view.isRefreshing = isSwipeRefreshing
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }
            if (
                addressBarHasFocus
                && suggestions.isNotEmpty()
                && userSettings.searchSuggestionEngine != SearchSuggestionEngineOption.NONE
            ) {
                AddressBarSearchSuggestions(
                    suggestions = suggestions,
                    onSelect = { selected ->
                        addressBarSearch(selected)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (!isLocked) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)) {
                FloatingMenuButton(
                    onHomeClick = {
                        focusManager.clearFocus()
                        WebViewNavigation.goHome(
                            ::customLoadUrl, systemSettings, userSettings
                        )
                    },
                    onLockClick = {
                        focusManager.clearFocus()
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

    if (
        userSettings.kioskControlPanelRegion != KioskControlPanelRegionOption.DISABLED
        || userSettings.backButtonHoldAction == BackButtonHoldActionOption.OPEN_KIOSK_CONTROL_PANEL
    ) {
        KioskControlPanel(10, ::customLoadUrl)
    }

    BackPressHandler(::customLoadUrl)

    BasicAuthDialog(authHandler, authHost, authRealm) { authHandler = null }

    LinkOptionsDialog(
        link = linkToOpen,
        onDismiss = { linkToOpen = null },
        onOpenLink = { url -> customLoadUrl(url) },
    )
}
