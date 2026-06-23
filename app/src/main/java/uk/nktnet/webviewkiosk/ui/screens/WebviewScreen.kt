package uk.nktnet.webviewkiosk.ui.screens

import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.URLUtil.isValidUrl
import android.webkit.WebView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.data.WebViewCreation
import uk.nktnet.webviewkiosk.config.option.AddressBarModeOption
import uk.nktnet.webviewkiosk.config.option.AddressBarPositionOption
import uk.nktnet.webviewkiosk.config.option.FloatingToolbarModeOption
import uk.nktnet.webviewkiosk.config.option.SearchSuggestionEngineOption
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundErrorCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundGoBackCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundGoForwardCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundGoHomeCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundGoToUrlCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundLockCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundPageDownCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundPageUpCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRefreshCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSearchCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundUnlockCommand
import uk.nktnet.webviewkiosk.handlers.BackPressHandler
import uk.nktnet.webviewkiosk.handlers.DimScreenOnInactivityTimeoutHandler
import uk.nktnet.webviewkiosk.handlers.ResetOnInactivityTimeoutHandler
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.PdfJsManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.ui.components.setting.BasicAuthDialog
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.AppLauncherDialog
import uk.nktnet.webviewkiosk.ui.components.webview.AddressBar
import uk.nktnet.webviewkiosk.ui.components.webview.AddressBarSearchSuggestions
import uk.nktnet.webviewkiosk.ui.components.webview.BookmarksDialog
import uk.nktnet.webviewkiosk.ui.components.webview.FloatingToolbar
import uk.nktnet.webviewkiosk.ui.components.webview.HistoryDialog
import uk.nktnet.webviewkiosk.ui.components.webview.ImageOptionsDialog
import uk.nktnet.webviewkiosk.ui.components.webview.KioskControlPanel
import uk.nktnet.webviewkiosk.ui.components.webview.LinkOptionsDialog
import uk.nktnet.webviewkiosk.ui.components.webview.LocalFilesDialog
import uk.nktnet.webviewkiosk.ui.components.webview.WebViewFindBar
import uk.nktnet.webviewkiosk.ui.components.webview.WebviewAwareSwipeRefreshLayout
import uk.nktnet.webviewkiosk.ui.placeholders.WebViewUnavailable
import uk.nktnet.webviewkiosk.utils.WebViewConfig
import uk.nktnet.webviewkiosk.utils.createCustomWebview
import uk.nktnet.webviewkiosk.utils.enterImmersiveMode
import uk.nktnet.webviewkiosk.utils.exitImmersiveMode
import uk.nktnet.webviewkiosk.utils.getMimeType
import uk.nktnet.webviewkiosk.utils.isSupportedFileURLMimeType
import uk.nktnet.webviewkiosk.utils.shouldBeImmersed
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.tryUnlockTask
import uk.nktnet.webviewkiosk.utils.unlockWithAuthIfRequired
import uk.nktnet.webviewkiosk.utils.webview.NfcBridgeManager
import uk.nktnet.webviewkiosk.utils.webview.SchemeType
import uk.nktnet.webviewkiosk.utils.webview.SearchSuggestionEngine
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation
import uk.nktnet.webviewkiosk.utils.webview.getBlockInfo
import uk.nktnet.webviewkiosk.utils.webview.html.generateFileMissingPage
import uk.nktnet.webviewkiosk.utils.webview.html.generatePdfRendererHtml
import uk.nktnet.webviewkiosk.utils.webview.html.generateUnsupportedMimeTypePage
import uk.nktnet.webviewkiosk.utils.webview.isCustomBlockPageUrl
import uk.nktnet.webviewkiosk.utils.webview.loadBlockedPage
import uk.nktnet.webviewkiosk.utils.webview.resolveUrlOrSearch
import java.io.File
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

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
    var imageToOpen by remember { mutableStateOf<String?>(null) }
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
            scope.launch {
                delay(100.milliseconds)
                awaitFrame()
                runCatching {
                    findInPageFocusRequester.requestFocus()
                }
            }
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
                delay(300.milliseconds)
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

    DisposableEffect(activity, isLocked) {
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
                delay(1000.milliseconds)
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

    val webViewCreation = createCustomWebview(
        context = context,
        config = WebViewConfig(
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
            onImageLongClick = { image ->
                imageToOpen = image
            },
        )
    )

    val (webView, webViewError) = when (webViewCreation) {
        is WebViewCreation.Success -> webViewCreation.webView to null
        is WebViewCreation.Failure -> null to webViewCreation.error
    }

    if (webView == null) {
        WebViewUnavailable(navController, webViewError)
        return
    }

    DisposableEffect(webView) {
        onDispose {
            NfcBridgeManager.detachWebView(webView)
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
                (
                    userSettings.supportPdfRendering
                        && PdfJsManager.areAssetsReady(context)
                        && (
                            mimeType == "application/pdf"
                            || file.extension.lowercase() == "pdf"
                        )
                ) -> {
                    generatePdfRendererHtml(newUrl)
                }
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
                    newUrl
                )
                return
            }
        }

        val isPdfRenderingSupported = (
            userSettings.supportPdfRendering
            && PdfJsManager.areAssetsReady(context)
        )
        val isWebPdf = (
            schemeType == SchemeType.WEB
            && uri.path?.lowercase()?.endsWith(".pdf") == true
        )
        val isDummyFallback = newUrl.startsWith(Constants.PDF_JS_ASSETS_DUMMY_URL)

        if (isPdfRenderingSupported && (isWebPdf || isDummyFallback)) {
            val targetPdfUrl = if (isDummyFallback) {
                uri.getQueryParameter("wk_pdf_url") ?: ""
            } else {
                newUrl
            }
            if (targetPdfUrl.isNotEmpty()) {
                handlePdfRemoteUrlRendering(
                    context,
                    webView,
                    targetPdfUrl,
                )
                return
            }
        } else if (isDummyFallback) {
            val pdfUrl = uri.getQueryParameter("wk_pdf_url") ?: ""
            if (pdfUrl.isNotEmpty()) {
                customLoadUrl(pdfUrl)
                return
            }
        }
        webView.loadUrl(newUrl)
    }

    val addressBarSearch: (String) -> Unit = { input ->
        val searchUrl = resolveUrlOrSearch(
            userSettings.searchProviderUrl, input.trim()
        )
        if (
            searchUrl.isNotBlank()
            && (
                searchUrl != systemSettings.currentUrl
                || userSettings.allowRefresh
            )
        ) {
            customLoadUrl(searchUrl)
        }
    }

    if (
        userSettings.refreshOnLoadingErrorIntervalSeconds
        >= Constants.MIN_REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS
    ) {
        LaunchedEffect(lastErrorUrl) {
            while (lastErrorUrl.isNotEmpty()) {
                delay(
                    (userSettings.refreshOnLoadingErrorIntervalSeconds * 1000).milliseconds
                )
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
    ImageOptionsDialog(
        webView = webView,
        imageUrl = imageToOpen,
        onDismiss = { imageToOpen = null },
        onOpenImage = { url -> customLoadUrl(url) }
    )

    LaunchedEffect(Unit) {
        RemoteMessageManager.commands.collect { command ->
            when (command.message) {
                is InboundGoBackCommand -> WebViewNavigation.goBack(::customLoadUrl, systemSettings)
                is InboundGoForwardCommand -> WebViewNavigation.goForward(::customLoadUrl, systemSettings)
                is InboundGoHomeCommand -> WebViewNavigation.goHome(::customLoadUrl, systemSettings, userSettings)
                is InboundRefreshCommand -> WebViewNavigation.refresh(::customLoadUrl, systemSettings, userSettings)
                is InboundGoToUrlCommand -> customLoadUrl(command.message.data.url)
                is InboundSearchCommand -> addressBarSearch(command.message.data.query)
                is InboundLockCommand -> tryLockTask(activity)
                is InboundUnlockCommand -> tryUnlockTask(activity)
                is InboundPageUpCommand -> { webView.pageUp(command.message.data.absolute) }
                is InboundPageDownCommand -> { webView.pageDown(command.message.data.absolute) }
                is InboundErrorCommand -> {
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
        { _, index ->
            WebViewNavigation.navigateToIndex(
                ::customLoadUrl,
                systemSettings,
                index
            )
        }
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

private fun handlePdfRemoteUrlRendering(
    context: Context,
    webView: WebView,
    targetPdfUrl: String
) {
    if (targetPdfUrl.isEmpty()) {
        return
    }

    Thread {
        try {
            ToastManager.show(context, "Preparing to render PDF...")
            val bytes = URL(targetPdfUrl).openStream().use { it.readBytes() }
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            (context as? android.app.Activity)?.runOnUiThread {
                val htmlContent = generatePdfRendererHtml(base64Data)
                val encodedPdfUrl = java.net.URLEncoder.encode(targetPdfUrl, "UTF-8")
                val baseUrlWithFallback = "${Constants.PDF_JS_ASSETS_DUMMY_URL}?wk_pdf_url=$encodedPdfUrl"

                webView.loadDataWithBaseURL(
                    baseUrlWithFallback,
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        } catch (e: Exception) {
            Log.e(Constants.APP_SCHEME, "Failed to fetch PDF: $targetPdfUrl", e)
            ToastManager.show(context, "PDF fetch failed: ${e.message}")
        }
    }.start()
}
