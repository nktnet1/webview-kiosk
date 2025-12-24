package com.nktnet.webview_kiosk.ui.screens

import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.URLUtil.isValidUrl
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.AddressBarModeOption
import com.nktnet.webview_kiosk.config.option.AddressBarPositionOption
import com.nktnet.webview_kiosk.config.option.FloatingToolbarModeOption
import com.nktnet.webview_kiosk.config.option.SearchSuggestionEngineOption
import com.nktnet.webview_kiosk.handlers.DimScreenOnInactivityTimeoutHandler
import com.nktnet.webview_kiosk.handlers.BackPressHandler
import com.nktnet.webview_kiosk.handlers.ResetOnInactivityTimeoutHandler
import com.nktnet.webview_kiosk.handlers.KioskControlPanel
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttErrorCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttGoBackCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttGoForwardCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttLockCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttGoHomeCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttGoToUrlCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttPageDownCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttPageUpCommand
import com.nktnet.webview_kiosk.managers.MqttManager
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttRefreshCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttUnlockCommand
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttSearchCommand
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.states.LockStateSingleton
import com.nktnet.webview_kiosk.ui.components.webview.AddressBar
import com.nktnet.webview_kiosk.ui.components.webview.FloatingToolbar
import com.nktnet.webview_kiosk.ui.components.webview.WebviewAwareSwipeRefreshLayout
import com.nktnet.webview_kiosk.ui.components.setting.BasicAuthDialog
import com.nktnet.webview_kiosk.ui.components.setting.dialog.AppLauncherDialog
import com.nktnet.webview_kiosk.ui.components.webview.AddressBarSearchSuggestions
import com.nktnet.webview_kiosk.ui.components.webview.BookmarksDialog
import com.nktnet.webview_kiosk.ui.components.webview.HistoryDialog
import com.nktnet.webview_kiosk.ui.components.webview.LinkOptionsDialog
import com.nktnet.webview_kiosk.ui.components.webview.LocalFilesDialog
import com.nktnet.webview_kiosk.ui.components.webview.WebViewFindBar
import com.nktnet.webview_kiosk.utils.createCustomWebview
import com.nktnet.webview_kiosk.utils.enterImmersiveMode
import com.nktnet.webview_kiosk.utils.exitImmersiveMode
import com.nktnet.webview_kiosk.utils.getMimeType
import com.nktnet.webview_kiosk.utils.isSupportedFileURLMimeType
import com.nktnet.webview_kiosk.utils.shouldBeImmersed
import com.nktnet.webview_kiosk.utils.tryLockTask
import com.nktnet.webview_kiosk.utils.tryUnlockTask
import com.nktnet.webview_kiosk.utils.unlockWithAuthIfRequired
import com.nktnet.webview_kiosk.utils.webview.SchemeType
import com.nktnet.webview_kiosk.utils.webview.SearchSuggestionEngine
import com.nktnet.webview_kiosk.utils.webview.WebViewNavigation
import com.nktnet.webview_kiosk.utils.webview.getBlockInfo
import com.nktnet.webview_kiosk.utils.webview.html.generateFileMissingPage
import com.nktnet.webview_kiosk.utils.webview.html.generateUnsupportedMimeTypePage
import com.nktnet.webview_kiosk.utils.webview.isCustomBlockPageUrl
import com.nktnet.webview_kiosk.utils.webview.loadBlockedPage
import com.nktnet.webview_kiosk.utils.webview.resolveUrlOrSearch
import java.io.File

@Composable
fun WebviewScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val focusManager = LocalFocusManager.current

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val isLocked by LockStateSingleton.isLocked
    val scope = rememberCoroutineScope()

    val lastVisitedUrl = systemSettings.currentUrl.takeIf { it.isNotEmpty() } ?: userSettings.homeUrl
    var urlBarText by remember {
        mutableStateOf(
            TextFieldValue(
                lastVisitedUrl
            )
        )
    }

    var mqttLastPublishedUrlJob: Job? = null
    var mqttLastPublishedUrl by remember { mutableStateOf(lastVisitedUrl) }

    var isOpenBookmarkDialog by remember { mutableStateOf(false) }
    var isOpenHistoryDialog by remember { mutableStateOf(false) }
    var isOpenFilesDialog by remember { mutableStateOf(false) }
    var isOpenAppsDialog by remember { mutableStateOf(false) }

    var isSwipeRefreshing by remember { mutableStateOf(false) }
    var addressBarHasFocus by remember { mutableStateOf(false) }

    var linkToOpen by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }

    val showAddressBar = when (userSettings.addressBarMode) {
        AddressBarModeOption.SHOWN -> true
        AddressBarModeOption.HIDDEN -> false
        AddressBarModeOption.HIDDEN_WHEN_LOCKED -> !isLocked
    }

    val showFloatingToolbar = when (userSettings.floatingToolbarMode) {
        FloatingToolbarModeOption.SHOWN -> true
        FloatingToolbarModeOption.HIDDEN -> false
        FloatingToolbarModeOption.HIDDEN_WHEN_LOCKED -> !isLocked
    }

    var authHandler by remember { mutableStateOf<HttpAuthHandler?>(null) }
    var authHost by remember { mutableStateOf<String?>(null) }
    var authRealm by remember { mutableStateOf<String?>(null) }

    var isActiveFindInPage by remember { mutableStateOf(false) }
    val findInPageFocusRequester = remember { FocusRequester() }
    val showFindInPage: () -> Unit = {
        if (!isActiveFindInPage) {
            isActiveFindInPage = true
        } else {
            findInPageFocusRequester.requestFocus()
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
        if (
            userSettings.mqttEnabled
            && url.trimEnd('/') != mqttLastPublishedUrl.trimEnd('/')
        ) {
            mqttLastPublishedUrlJob?.cancel()
            mqttLastPublishedUrlJob = scope.launch {
                delay(1000)
                MqttManager.publishUrlChangedEvent(url)
                mqttLastPublishedUrl = url
            }
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
        config = com.nktnet.webview_kiosk.utils.WebViewConfig(
            systemSettings = systemSettings,
            userSettings = userSettings,
            blacklistRegexes = blacklistRegexes,
            whitelistRegexes = whitelistRegexes,
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

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
    }

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
        val uri = newUrl.toUri()

        if (isCustomBlockPageUrl(schemeType, uri)) {
            val blockUrl = uri.getQueryParameter("url")
            if (blockUrl != null) {
                webView.loadUrl(blockUrl)
                return
            }
        } else if (schemeType == SchemeType.FILE) {
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

    val addressBarSearch: (String) -> Unit = { input ->
        val searchUrl = resolveUrlOrSearch(
            userSettings.searchProviderUrl, input.trim()
        )
        if (searchUrl.isNotBlank() && (searchUrl != systemSettings.currentUrl || userSettings.allowRefresh)) {
            customLoadUrl(searchUrl)
        }
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

    val composableAddressBarView = @Composable {
        AndroidView(
            factory = { ctx ->
                ComposeView(ctx).apply {
                    setContent {
                        AddressBar(
                            navController = navController,
                            urlBarText = urlBarText,
                            onUrlBarTextChange = { urlBarText = it },
                            hasFocus = addressBarHasFocus,
                            onFocusChanged = { addressBarHasFocus = it.isFocused },
                            showFindInPage = showFindInPage,
                            addressBarSearch = addressBarSearch,
                            showHistoryDialog = { isOpenHistoryDialog = true },
                            showBookmarkDialog = { isOpenBookmarkDialog = true },
                            showFilesDialog = { isOpenFilesDialog = true },
                            showAppsDialog = { isOpenAppsDialog = true },
                            webView = webView,
                            customLoadUrl = ::customLoadUrl,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    val composableWebView = @Composable {
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

                WebviewAwareSwipeRefreshLayout(ctx, webView).apply {
                    isEnabled = userSettings.allowRefresh && userSettings.allowPullToRefresh
                    setOnRefreshListener {
                        isSwipeRefreshing = true
                        WebViewNavigation.refresh(
                            ::customLoadUrl,
                            systemSettings,
                            userSettings
                        )
                    }
                    addView(
                        webView.apply {
                            customLoadUrl(initialUrl)
                        }
                    )
                }
            },
            update = { view ->
                view.isRefreshing = isSwipeRefreshing
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(userSettings.webViewInset.toWindowInsets())
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showAddressBar && userSettings.addressBarPosition == AddressBarPositionOption.TOP) {
                composableAddressBarView()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
            ) {
                composableWebView()

                if (progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
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
                        modifier = Modifier.align(
                            if (
                                userSettings.addressBarPosition == AddressBarPositionOption.TOP
                            ) {
                                Alignment.TopStart
                            } else {
                                Alignment.BottomStart
                            }
                        )
                    )
                }
            }

            WebViewFindBar(
                webView = webView,
                isActiveFindInPage = isActiveFindInPage,
                onActiveChange = { isActiveFindInPage = it },
                focusRequester = findInPageFocusRequester,
            )

            if (showAddressBar && userSettings.addressBarPosition == AddressBarPositionOption.BOTTOM) {
                composableAddressBarView()
            }
        }

        if (showFloatingToolbar) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
            ) {
                FloatingToolbar(
                    onHomeClick = {
                        focusManager.clearFocus()
                        WebViewNavigation.goHome(
                            ::customLoadUrl, systemSettings, userSettings
                        )
                    },
                    onLockClick = {
                        focusManager.clearFocus()
                        tryLockTask(activity)
                    },
                    onUnlockClick = {
                        activity?.let {
                            focusManager.clearFocus()
                            unlockWithAuthIfRequired(activity)
                        }
                    },
                    navController = navController
                )
            }
        }
    }

    if (userSettings.resetOnInactivitySeconds >= Constants.MIN_INACTIVITY_TIMEOUT_SECONDS) {
        ResetOnInactivityTimeoutHandler(::customLoadUrl)
    }

    if (userSettings.dimScreenOnInactivitySeconds >= Constants.MIN_INACTIVITY_TIMEOUT_SECONDS) {
        DimScreenOnInactivityTimeoutHandler()
    }

    KioskControlPanel(
        navController = navController,
        requiredTaps = 10,
        showFindInPage = showFindInPage,
        showHistoryDialog = { isOpenHistoryDialog = true },
        showBookmarkDialog = { isOpenBookmarkDialog = true },
        showFilesDialog = { isOpenFilesDialog = true },
        showAppsDialog = { isOpenAppsDialog = true },
        webView = webView,
        customLoadUrl = ::customLoadUrl,
    )

    BackPressHandler(::customLoadUrl)

    BasicAuthDialog(authHandler, authHost, authRealm) { authHandler = null }

    LinkOptionsDialog(
        link = linkToOpen,
        onDismiss = { linkToOpen = null },
        onOpenLink = { url -> customLoadUrl(url) },
    )

    LaunchedEffect(Unit) {
        MqttManager.commands.collect { command ->
            when (command) {
                is MqttGoBackCommand -> WebViewNavigation.goBack(::customLoadUrl, systemSettings)
                is MqttGoForwardCommand -> WebViewNavigation.goForward(::customLoadUrl, systemSettings)
                is MqttGoHomeCommand -> WebViewNavigation.goHome(::customLoadUrl, systemSettings, userSettings)
                is MqttRefreshCommand -> WebViewNavigation.refresh(::customLoadUrl, systemSettings, userSettings)
                is MqttGoToUrlCommand -> customLoadUrl(command.data.url)
                is MqttSearchCommand -> addressBarSearch(command.data.query)
                is MqttLockCommand -> tryLockTask(activity)
                is MqttUnlockCommand -> tryUnlockTask(activity)
                is MqttPageUpCommand -> { webView.pageUp(command.data.absolute) }
                is MqttPageDownCommand -> { webView.pageDown(command.data.absolute) }
                is MqttErrorCommand -> {
                    ToastManager.show(
                        context,
                        "Received invalid MQTT command. See debug logs in MQTT settings."
                    )
                }
                else -> Unit
            }
        }
    }

    HistoryDialog(
        isOpenHistoryDialog,
        { isOpenHistoryDialog = false },
        ::customLoadUrl
    )

    BookmarksDialog(
        isOpenBookmarkDialog,
        { isOpenBookmarkDialog = false },
        ::customLoadUrl
    )

    LocalFilesDialog(
        isOpenFilesDialog,
        { isOpenFilesDialog = false },
        ::customLoadUrl
    )

    AppLauncherDialog(
        showDialog = isOpenAppsDialog,
        onDismiss = { isOpenAppsDialog = false }
    )
}
