package uk.nktnet.webviewkiosk.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import android.webkit.URLUtil.isValidUrl
import android.webkit.WebView
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
import uk.nktnet.webviewkiosk.blocking.WebViewBlocker
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.data.WebViewCreation
import uk.nktnet.webviewkiosk.config.option.AddressBarModeOption
import uk.nktnet.webviewkiosk.config.option.AddressBarPositionOption
import uk.nktnet.webviewkiosk.config.option.FloatingToolbarModeOption
import uk.nktnet.webviewkiosk.config.option.RefreshOnNetworkAvailableOption
import uk.nktnet.webviewkiosk.config.option.ResetActionOption
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
import uk.nktnet.webviewkiosk.managers.NetworkManager
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
import uk.nktnet.webviewkiosk.ui.components.webview.ScreenWakeRefreshController
import uk.nktnet.webviewkiosk.ui.components.webview.WebViewFindBar
import uk.nktnet.webviewkiosk.ui.components.webview.WebviewAwareSwipeRefreshLayout
import uk.nktnet.webviewkiosk.ui.placeholders.WebViewUnavailable
import uk.nktnet.webviewkiosk.utils.ProvideSystemSafeClipboard
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
import uk.nktnet.webviewkiosk.utils.webview.BackgroundMediaPlaybackController
import uk.nktnet.webviewkiosk.utils.webview.HttpAuthRequest
import uk.nktnet.webviewkiosk.utils.webview.PAUSE_MEDIA_SCRIPT
import uk.nktnet.webviewkiosk.utils.webview.SchemeType
import uk.nktnet.webviewkiosk.utils.webview.SearchSuggestionEngine
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation
import uk.nktnet.webviewkiosk.utils.webview.clearSelectedResetData
import uk.nktnet.webviewkiosk.utils.webview.getBlockInfo
import uk.nktnet.webviewkiosk.utils.webview.handlers.registerPdfSource
import uk.nktnet.webviewkiosk.utils.webview.html.BlockCause
import uk.nktnet.webviewkiosk.utils.webview.html.generateFileMissingPage
import uk.nktnet.webviewkiosk.utils.webview.html.generatePdfRendererHtml
import uk.nktnet.webviewkiosk.utils.webview.html.generateUnsupportedMimeTypePage
import uk.nktnet.webviewkiosk.utils.webview.loadBlockedPage
import uk.nktnet.webviewkiosk.utils.webview.resolveBlockPageTarget
import uk.nktnet.webviewkiosk.utils.webview.resolveUrlOrSearch

// All API levels use Compose IME insets when edge-to-edge is active. API 29
// previously relied on framework resize only because edge-to-edge was cleared.
fun Modifier.imePaddingCompat(): Modifier = this.imePadding()

@Composable
fun WebviewScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isCurrentEntry = currentBackStackEntry === lifecycleOwner
    val focusManager = LocalFocusManager.current

    // A refresh can animate two entries with the same route; compare their owners
    // instead.
    fun canHandleEvents(): Boolean =
        navController.currentBackStackEntry === lifecycleOwner &&
            (activity as? MainActivity)?.isApplicationEventHost() != false

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val contentBlocker = remember(context) { ContentBlockingService.get(context) }
    val isLocked by LockStateSingleton.isLocked
    val scope = rememberCoroutineScope()

    val lastVisitedUrl =
        systemSettings.currentUrl.takeIf { it.isNotEmpty() } ?: userSettings.homeUrl
    var urlBarText by remember {
        mutableStateOf(TextFieldValue(lastVisitedUrl))
    }

    var mqttLastPublishedUrl by remember { mutableStateOf(lastVisitedUrl) }

    var isOpenBookmarkDialog by remember { mutableStateOf(false) }
    var isOpenHistoryDialog by remember { mutableStateOf(false) }
    var isOpenFilesDialog by remember { mutableStateOf(false) }
    var isOpenAppsDialog by remember { mutableStateOf(false) }

    var addressBarHasFocus by remember { mutableStateOf(false) }

    var linkToOpen by remember { mutableStateOf<String?>(null) }
    var imageToOpen by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(100) }
    var isPageLoading by remember { mutableStateOf(false) }

    val showAddressBar =
        when (userSettings.addressBarMode) {
            AddressBarModeOption.SHOWN -> true
            AddressBarModeOption.HIDDEN -> false
            AddressBarModeOption.HIDDEN_WHEN_LOCKED -> !isLocked
        }

    val showFloatingToolbar =
        when (userSettings.floatingToolbarMode) {
            FloatingToolbarModeOption.SHOWN -> true
            FloatingToolbarModeOption.HIDDEN -> false
            FloatingToolbarModeOption.HIDDEN_WHEN_LOCKED -> !isLocked
        }

    var authRequest by remember { mutableStateOf<HttpAuthRequest?>(null) }
    DisposableEffect(authRequest) {
        val request = authRequest
        onDispose { request?.cancel() }
    }

    var isActiveFindInPage by remember { mutableStateOf(false) }
    val findInPageFocusRequester = remember { FocusRequester() }
    var findInPageFocusRequestKey by remember { mutableIntStateOf(0) }
    val showFindInPage: () -> Unit = {
        isActiveFindInPage = true
        findInPageFocusRequestKey++
    }

    val blacklistRegexes: List<String> by lazy {
        userSettings.websiteBlacklist.lines().mapNotNull {
            it.trim().takeIf(String::isNotEmpty)
        }
    }

    val whitelistRegexes: List<String> by lazy {
        userSettings.websiteWhitelist.lines().mapNotNull {
            it.trim().takeIf(String::isNotEmpty)
        }
    }

    var lastErrorUrl by remember { mutableStateOf("") }

    var localNetworkPermissionRequestInFlight by remember { mutableStateOf(false) }
    var retryAfterLocalNetworkPermission by remember { mutableStateOf(false) }
    val localNetworkPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            localNetworkPermissionRequestInFlight = false
            if (isGranted && !LockStateSingleton.isLocked.value) {
                retryAfterLocalNetworkPermission = true
            }
        }

    var suggestions by remember { mutableStateOf(listOf<String>()) }
    var webViewRecreationKey by remember { mutableIntStateOf(0) }
    var pendingResetHomeUrl by remember { mutableStateOf<String?>(null) }
    var clearHistoryAfterReset by remember { mutableStateOf(false) }
    val creationKey = webViewRecreationKey
    var mqttLastPublishedUrlJob by
        remember(creationKey) { mutableStateOf<Job?>(null) }

    fun isCurrentWebView(): Boolean =
        canHandleEvents() && creationKey == webViewRecreationKey

    if (userSettings.searchSuggestionEngine != SearchSuggestionEngineOption.NONE) {
        LaunchedEffect(addressBarHasFocus, urlBarText.text) {
            if (
                addressBarHasFocus &&
                    urlBarText.text.isNotBlank() &&
                    !isValidUrl(urlBarText.text)
            ) {
                delay(300.milliseconds)
                suggestions =
                    try {
                        withContext(Dispatchers.IO) {
                            SearchSuggestionEngine.suggest(
                                userSettings.searchSuggestionEngine,
                                urlBarText.text,
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

    DisposableEffect(activity, isLocked, isCurrentEntry) {
        if (activity != null && isCurrentEntry && canHandleEvents()) {
            val shouldImmerse = shouldBeImmersed(activity, userSettings)
            if (shouldImmerse) {
                enterImmersiveMode(activity)
            } else {
                exitImmersiveMode(activity)
            }
        }
        onDispose {
            // A replacement WebView may already have set the same window's immersive
            // mode
            // while this outgoing entry is still fading out.
            if (
                navController.currentBackStackEntry === lifecycleOwner ||
                    navController.currentDestination?.route != Screen.WebView.route
            ) {
                activity?.let { exitImmersiveMode(it) }
            }
        }
    }

    fun updateAddressBar(url: String) {
        if (!isCurrentWebView()) {
            return
        }
        if (!addressBarHasFocus) {
            urlBarText = urlBarText.copy(text = url)
        }
        mqttLastPublishedUrlJob?.cancel()
        if (
            userSettings.mqttEnabled &&
                url.trimEnd('/') != mqttLastPublishedUrl.trimEnd('/')
        ) {
            mqttLastPublishedUrlJob = scope.launch {
                delay(1000.milliseconds)
                if (isCurrentWebView() && userSettings.mqttEnabled) {
                    MqttManager.publishUrlChangedEvent(url)
                    mqttLastPublishedUrl = url
                }
            }
        }
    }

    val webViewCreation =
        createCustomWebview(
            context = context,
            config =
                WebViewConfig(
                    systemSettings = systemSettings,
                    userSettings = userSettings,
                    blacklistRegexes = blacklistRegexes,
                    whitelistRegexes = whitelistRegexes,
                    isActive = ::isCurrentWebView,
                    setLastErrorUrl = { errorUrl ->
                        lastErrorUrl = errorUrl
                    },
                    onLocalNetworkPermissionMissing = {
                        if (
                            Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.CINNAMON_BUN &&
                                !LockStateSingleton.isLocked.value &&
                                !localNetworkPermissionRequestInFlight &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_LOCAL_NETWORK,
                                ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            localNetworkPermissionRequestInFlight = true
                            localNetworkPermissionLauncher.launch(
                                Manifest.permission.ACCESS_LOCAL_NETWORK
                            )
                        }
                    },
                    onProgressChanged = { newProgress -> progress = newProgress },
                    onLoadingStateChanged = { isLoading ->
                        isPageLoading = isLoading
                    },
                    finishSwipeRefresh = { finishedWebView ->
                        // A fast load can finish before Compose runs an update.
                        // Complete the native gesture on its owning view.
                        (finishedWebView.parent as? WebviewAwareSwipeRefreshLayout)
                            ?.isRefreshing = false
                    },
                    updateAddressBarAndHistory = { url, _ -> updateAddressBar(url) },
                    onHttpAuthRequest = { request ->
                        if (isCurrentWebView()) {
                            authRequest?.cancel()
                            authRequest = request
                        } else {
                            request?.cancel()
                        }
                    },
                    onLinkLongClick = { link ->
                        linkToOpen = link
                    },
                    onImageLongClick = { image ->
                        imageToOpen = image
                    },
                    onPdfUrlRequested = ::handlePdfUrlRendering,
                    onRenderProcessGone = {
                        webViewRecreationKey++
                    },
                    onMainFrameLoadFinished = { finishedWebView ->
                        if (clearHistoryAfterReset && isCurrentWebView()) {
                            finishedWebView.clearHistory()
                            clearHistoryAfterReset = false
                        }
                    },
                ),
            recreationKey = webViewRecreationKey,
        )

    val (webView, webViewError) =
        when (webViewCreation) {
            is WebViewCreation.Success -> webViewCreation.webView to null
            is WebViewCreation.Failure -> null to webViewCreation.error
        }

    if (webView == null) {
        WebViewUnavailable(navController, webViewError)
        return
    }

    // The default is intentionally foreground-only. Pause HTML media when the
    // host Activity becomes invisible, not when a picker/dialog briefly takes
    // focus. Leave enabled WebViews running and let their playback service own
    // background lifetime.
    DisposableEffect(activity, webView, userSettings.backgroundMediaPlayback) {
        val activityLifecycle = (activity as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (userSettings.backgroundMediaPlayback) {
                val controller =
                    webView.getTag(R.id.background_media_playback_controller)
                        as? BackgroundMediaPlaybackController
                if (
                    event == Lifecycle.Event.ON_PAUSE ||
                        event == Lifecycle.Event.ON_STOP
                ) {
                    controller?.onHostBackgroundChanged(true)
                }
                if (
                    event == Lifecycle.Event.ON_START ||
                        event == Lifecycle.Event.ON_RESUME
                ) {
                    controller?.onActivityStarted()
                }
            } else {
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        if (userSettings.enableJavaScript) {
                            webView.evaluateJavascript(PAUSE_MEDIA_SCRIPT, null)
                        }
                        webView.onPause()
                    }
                    Lifecycle.Event.ON_START -> webView.onResume()
                    else -> Unit
                }
            }
        }
        activityLifecycle?.addObserver(observer)
        onDispose { activityLifecycle?.removeObserver(observer) }
    }

    DisposableEffect(webView) {
        onDispose {
            mqttLastPublishedUrlJob?.cancel()
            (webViewCreation as? WebViewCreation.Success)?.onDispose?.invoke()
        }
    }

    fun customLoadUrl(newUrl: String) {
        if (!isCurrentWebView()) {
            return
        }
        val resolved =
            resolveBlockPageTarget(
                newUrl,
                blacklistRegexes,
                whitelistRegexes,
                userSettings,
            ) ?: return
        val resolvedUrl = resolved.url
        val originalPolicy = resolved.blockCause
        val url =
            if (originalPolicy == null) {
                WebViewBlocker.documentUrl(resolvedUrl, "GET", contentBlocker)
            } else {
                resolvedUrl
            }
        systemSettings.urlBeingHandled = url
        val (schemeType, policyBlockCause) =
            if (url == resolvedUrl) {
                resolved.schemeType to resolved.blockCause
            } else {
                getBlockInfo(
                    url = url,
                    blacklistRegexes = blacklistRegexes,
                    whitelistRegexes = whitelistRegexes,
                    userSettings = userSettings,
                )
            }
        val blockCause =
            originalPolicy
                ?: policyBlockCause
                ?: if (
                    WebViewBlocker.shouldBlockDocument(url, "GET", contentBlocker)
                ) {
                    BlockCause.CONTENT_BLOCKING
                } else {
                    null
                }
        if (blockCause != null) {
            loadBlockedPage(
                webView,
                userSettings,
                url,
                blockCause,
            )
            return
        }
        val uri = url.toUri()

        if (schemeType == SchemeType.FILE) {
            val mimeType = getMimeType(context, uri)
            val file = File(uri.path ?: "")
            val isPdf =
                (mimeType == "application/pdf" ||
                    file.extension.lowercase() == "pdf")

            if (
                file.exists() &&
                    isPdf &&
                    userSettings.supportPdfRendering &&
                    PdfJsManager.areAssetsReady(context)
            ) {
                handlePdfUrlRendering(webView, url)
                return
            }

            val pageContent =
                when {
                    !file.exists() ->
                        generateFileMissingPage(file, userSettings.theme)
                    !isSupportedFileURLMimeType(mimeType) ->
                        generateUnsupportedMimeTypePage(
                            context,
                            file,
                            mimeType,
                            userSettings.theme,
                        )
                    else -> null
                }
            pageContent?.let {
                webView.loadDataWithBaseURL(
                    url,
                    it,
                    "text/html",
                    "UTF-8",
                    null,
                )
                return
            }
        }

        val isPdfRenderingSupported =
            (userSettings.supportPdfRendering &&
                PdfJsManager.areAssetsReady(context))
        val isWebPdf =
            (schemeType == SchemeType.WEB &&
                uri.path?.lowercase()?.endsWith(".pdf") == true)
        if (isPdfRenderingSupported && isWebPdf) {
            handlePdfUrlRendering(
                webView,
                url,
            )
            return
        }
        webView.loadUrl(url)
    }

    val canRefreshOnWake by rememberUpdatedState {
        userSettings.refreshOnScreenWake && isCurrentWebView()
    }
    val refreshOnWake by rememberUpdatedState {
        customLoadUrl(systemSettings.currentUrl)
    }
    val refreshOnScreenWake = userSettings.refreshOnScreenWake
    DisposableEffect(webView, lifecycleOwner, activity, refreshOnScreenWake) {
        val controller =
            activity
                ?.takeIf { refreshOnScreenWake }
                ?.let {
                    ScreenWakeRefreshController(
                        context,
                        lifecycleOwner.lifecycle,
                        it.window.decorView,
                        canRefresh = { canRefreshOnWake() },
                        refresh = { refreshOnWake() },
                    )
                }
        controller?.attach()
        onDispose { controller?.detach() }
    }

    LaunchedEffect(webView, retryAfterLocalNetworkPermission) {
        if (retryAfterLocalNetworkPermission) {
            retryAfterLocalNetworkPermission = false
            if (isCurrentWebView() && !LockStateSingleton.isLocked.value) {
                webView.reload()
            }
        }
    }

    val isOnline by
        remember(context) {
                NetworkManager.isOnline(context)
            }
            .collectAsState(initial = true)
    var previousOnline by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(webView, isOnline) {
        if (
            isCurrentWebView() &&
                previousOnline != null &&
                previousOnline != isOnline
        ) {
            if (isOnline) {
                when (userSettings.refreshOnNetworkAvailable) {
                    RefreshOnNetworkAvailableOption.ALWAYS -> {
                        WebViewNavigation.refresh(
                            ::customLoadUrl,
                            systemSettings,
                            userSettings,
                        )
                    }
                    RefreshOnNetworkAvailableOption.ON_PAGE_ERROR -> {
                        if (lastErrorUrl.isNotEmpty()) {
                            WebViewNavigation.refresh(
                                ::customLoadUrl,
                                systemSettings,
                                userSettings,
                            )
                        }
                    }
                    RefreshOnNetworkAvailableOption.NEVER -> Unit
                }
            }
        }
        previousOnline = isOnline
    }

    val addressBarSearch: (String) -> Unit = { input ->
        val searchUrl =
            resolveUrlOrSearch(
                userSettings.searchProviderUrl,
                input.trim(),
            )
        if (
            searchUrl.isNotBlank() &&
                (searchUrl != systemSettings.currentUrl || userSettings.allowRefresh)
        ) {
            customLoadUrl(searchUrl)
        }
    }

    val loadingErrorInterval = userSettings.refreshOnLoadingErrorIntervalSeconds
    LaunchedEffect(webView, lastErrorUrl, loadingErrorInterval) {
        while (lastErrorUrl.isNotEmpty()) {
            val interval = userSettings.refreshOnLoadingErrorIntervalSeconds
            if (interval < Constants.MIN_REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS) {
                break
            }
            delay((interval * 1000L).milliseconds)
            if (
                isCurrentWebView() &&
                    lastErrorUrl.isNotEmpty() &&
                    userSettings.refreshOnLoadingErrorIntervalSeconds >=
                        Constants.MIN_REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS
            ) {
                customLoadUrl(systemSettings.currentUrl)
            }
        }
    }

    // The initial policy is installed before loading the first page. If settings
    // change while this WebView stays mounted, update only when values change,
    // rather than invoking WebView's cookie APIs on every recomposition.
    val acceptCookies = userSettings.acceptCookies
    val acceptThirdPartyCookies = userSettings.acceptThirdPartyCookies
    LaunchedEffect(webView, acceptCookies, acceptThirdPartyCookies) {
        CookieManager.getInstance().apply {
            setAcceptCookie(acceptCookies)
            setAcceptThirdPartyCookies(webView, acceptThirdPartyCookies)
        }
    }

    val composableAddressBarView =
        @Composable {
            key(webView) {
                AndroidView(
                    factory = { ctx ->
                        ComposeView(ctx).apply {
                            setContent {
                                // Keep the separate ComposeView needed for WebView
                                // autofill.
                                ProvideSystemSafeClipboard {
                                    AddressBar(
                                        navController = navController,
                                        urlBarText = urlBarText,
                                        onUrlBarTextChange = { urlBarText = it },
                                        hasFocus = addressBarHasFocus,
                                        isPageLoading = isPageLoading,
                                        onFocusChanged = {
                                            addressBarHasFocus = it.isFocused
                                        },
                                        showFindInPage = showFindInPage,
                                        addressBarSearch = addressBarSearch,
                                        showHistoryDialog = {
                                            isOpenHistoryDialog = true
                                        },
                                        showBookmarkDialog = {
                                            isOpenBookmarkDialog = true
                                        },
                                        showFilesDialog = {
                                            isOpenFilesDialog = true
                                        },
                                        showAppsDialog = { isOpenAppsDialog = true },
                                        webView = webView,
                                        customLoadUrl = ::customLoadUrl,
                                    )
                                }
                            }
                        }
                    },
                    onRelease = { it.disposeComposition() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

    val composableWebView =
        @Composable {
            key(webView) {
                AndroidView(
                    factory = { ctx ->
                        var initialUrl = pendingResetHomeUrl ?: lastVisitedUrl

                        if (pendingResetHomeUrl != null) {
                            pendingResetHomeUrl = null
                        } else if (systemSettings.intentUrl.isNotEmpty()) {
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
                            isEnabled =
                                userSettings.allowRefresh &&
                                    userSettings.allowPullToRefresh
                            setOnRefreshListener {
                                WebViewNavigation.refresh(
                                    ::customLoadUrl,
                                    systemSettings,
                                    userSettings,
                                )
                            }
                            addView(
                                webView.apply {
                                    customLoadUrl(initialUrl)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(userSettings.webViewInset.toWindowInsets())
                .imePaddingCompat()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (
                showAddressBar &&
                    userSettings.addressBarPosition == AddressBarPositionOption.TOP
            ) {
                composableAddressBarView()
            }

            Box(modifier = Modifier.weight(1f)) {
                composableWebView()

                if (userSettings.showLoadingProgressBar && progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress.coerceAtLeast(10) / 100f },
                        modifier =
                            Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }

                if (
                    addressBarHasFocus &&
                        suggestions.isNotEmpty() &&
                        userSettings.searchSuggestionEngine !=
                            SearchSuggestionEngineOption.NONE
                ) {
                    AddressBarSearchSuggestions(
                        suggestions = suggestions,
                        onSelect = { selected ->
                            addressBarSearch(selected)
                        },
                        modifier =
                            Modifier.align(
                                if (
                                    userSettings.addressBarPosition ==
                                        AddressBarPositionOption.TOP
                                ) {
                                    Alignment.TopStart
                                } else {
                                    Alignment.BottomStart
                                }
                            ),
                    )
                }
            }

            WebViewFindBar(
                webView = webView,
                isActiveFindInPage = isActiveFindInPage,
                onActiveChange = { isActiveFindInPage = it },
                focusRequester = findInPageFocusRequester,
                focusRequestKey = findInPageFocusRequestKey,
            )

            if (
                showAddressBar &&
                    userSettings.addressBarPosition ==
                        AddressBarPositionOption.BOTTOM
            ) {
                composableAddressBarView()
            }
        }

        if (showFloatingToolbar) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                FloatingToolbar(
                    onHomeClick = {
                        focusManager.clearFocus()
                        WebViewNavigation.goHome(
                            ::customLoadUrl,
                            systemSettings,
                            userSettings,
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
                    navController = navController,
                )
            }
        }
    }

    if (
        isCurrentEntry &&
            userSettings.resetOnInactivitySeconds >=
                Constants.MIN_INACTIVITY_TIMEOUT_SECONDS
    ) {
        ResetOnInactivityTimeoutHandler(
            onReset = {
                val actions = userSettings.resetActions
                clearSelectedResetData(webView, systemSettings, actions)
                if (isCurrentWebView()) {
                    if (ResetActionOption.RECREATE_WEBVIEW in actions) {
                        pendingResetHomeUrl = userSettings.homeUrl
                        webViewRecreationKey++
                    } else {
                        clearHistoryAfterReset = ResetActionOption.HISTORY in actions
                        customLoadUrl(userSettings.homeUrl)
                    }
                }
            },
            canHandleEvents = ::isCurrentWebView,
        )
    }

    if (
        isCurrentEntry &&
            userSettings.dimScreenOnInactivitySeconds >=
                Constants.MIN_INACTIVITY_TIMEOUT_SECONDS
    ) {
        DimScreenOnInactivityTimeoutHandler(::canHandleEvents)
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

    BackPressHandler(::customLoadUrl, ::isCurrentWebView)

    val displayedAuthRequest = authRequest
    BasicAuthDialog(displayedAuthRequest) {
        if (authRequest === displayedAuthRequest) {
            authRequest = null
        }
    }

    LinkOptionsDialog(
        link = linkToOpen,
        onDismiss = { linkToOpen = null },
        onOpenLink = { url -> customLoadUrl(url) },
    )
    ImageOptionsDialog(
        webView = webView,
        imageUrl = imageToOpen,
        onDismiss = { imageToOpen = null },
        onOpenImage = { url -> customLoadUrl(url) },
    )

    DisposableEffect(webView) {
        val unregister =
            RemoteMessageManager.registerWebViewCommandHandler(::isCurrentWebView) {
                command ->
                // Navigation fades can keep both the outgoing and incoming WebViews
                // composed.
                when (command) {
                    is InboundGoBackCommand ->
                        WebViewNavigation.goBack(::customLoadUrl, systemSettings)
                    is InboundGoForwardCommand ->
                        WebViewNavigation.goForward(::customLoadUrl, systemSettings)
                    is InboundGoHomeCommand ->
                        WebViewNavigation.goHome(
                            ::customLoadUrl,
                            systemSettings,
                            userSettings,
                        )
                    is InboundRefreshCommand ->
                        WebViewNavigation.refresh(
                            ::customLoadUrl,
                            systemSettings,
                            userSettings,
                        )
                    is InboundGoToUrlCommand -> customLoadUrl(command.data.url)
                    is InboundSearchCommand -> addressBarSearch(command.data.query)
                    is InboundLockCommand -> tryLockTask(activity)
                    is InboundUnlockCommand -> tryUnlockTask(activity)
                    is InboundPageUpCommand -> {
                        webView.pageUp(command.data.absolute)
                    }
                    is InboundPageDownCommand -> {
                        webView.pageDown(command.data.absolute)
                    }
                    is InboundErrorCommand -> {
                        ToastManager.show(
                            context,
                            context.getString(
                                R.string
                                    .ui_received_invalid_mqtt_command_see_debug_logs_in_mqtt_s
                            ),
                        )
                    }
                    else -> Unit
                }
            }
        onDispose(unregister)
    }

    HistoryDialog(
        isOpenHistoryDialog,
        { isOpenHistoryDialog = false },
        { _, index ->
            WebViewNavigation.navigateToIndex(
                ::customLoadUrl,
                systemSettings,
                index,
            )
        },
    )

    BookmarksDialog(
        isOpenBookmarkDialog,
        { isOpenBookmarkDialog = false },
        ::customLoadUrl,
    )

    LocalFilesDialog(
        isOpenFilesDialog,
        { isOpenFilesDialog = false },
        ::customLoadUrl,
    )

    AppLauncherDialog(
        showDialog = isOpenAppsDialog,
        onDismiss = { isOpenAppsDialog = false },
    )
}

private fun handlePdfUrlRendering(
    webView: WebView,
    targetPdfUrl: String,
) {
    if (targetPdfUrl.isEmpty()) {
        return
    }
    if (
        WebViewBlocker.shouldBlockDocument(
            targetPdfUrl,
            "GET",
            ContentBlockingService.get(webView.context),
        )
    ) {
        loadBlockedPage(
            webView,
            UserSettings(webView.context),
            targetPdfUrl,
            BlockCause.CONTENT_BLOCKING,
        )
        return
    }

    val currentToken = webView.getTag(R.id.pdf_source_token) as? String
    val sourceToken = registerPdfSource(targetPdfUrl, currentToken)
    val htmlContent = generatePdfRendererHtml(sourceToken)
    val encodedPdfUrl = java.net.URLEncoder.encode(targetPdfUrl, "UTF-8")
    val baseUrlWithFallback =
        "${Constants.PDF_JS_ASSETS_DUMMY_URL}?wk_pdf_url=$encodedPdfUrl&wk_pdf_token=$sourceToken"

    webView.setTag(R.id.pdf_source_token, sourceToken)
    webView.loadDataWithBaseURL(
        baseUrlWithFallback,
        htmlContent,
        "text/html",
        "UTF-8",
        targetPdfUrl,
    )
}
