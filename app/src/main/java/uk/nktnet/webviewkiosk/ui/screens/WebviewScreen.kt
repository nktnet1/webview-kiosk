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
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.AddressBarModeOption
import uk.nktnet.webviewkiosk.config.option.FloatingToolbarModeOption
import uk.nktnet.webviewkiosk.config.option.SearchSuggestionEngineOption
import uk.nktnet.webviewkiosk.handlers.DimScreenOnInactivityTimeoutHandler
import uk.nktnet.webviewkiosk.handlers.backbutton.BackPressHandler
import uk.nktnet.webviewkiosk.handlers.ResetOnInactivityTimeoutHandler
import uk.nktnet.webviewkiosk.handlers.KioskControlPanel
import uk.nktnet.webviewkiosk.mqtt.messages.MqttErrorCommand
import uk.nktnet.webviewkiosk.mqtt.messages.MqttGoBackMqttCommand
import uk.nktnet.webviewkiosk.mqtt.messages.MqttGoForwardMqttCommand
import uk.nktnet.webviewkiosk.mqtt.messages.MqttLockMqttCommand
import uk.nktnet.webviewkiosk.mqtt.messages.MqttGoHomeMqttCommand
import uk.nktnet.webviewkiosk.mqtt.messages.MqttGoToUrlMqttCommand
import uk.nktnet.webviewkiosk.mqtt.MqttManager
import uk.nktnet.webviewkiosk.mqtt.messages.MqttRefreshMqttCommand
import uk.nktnet.webviewkiosk.mqtt.messages.MqttUnlockMqttCommand
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.ui.components.webview.AddressBar
import uk.nktnet.webviewkiosk.ui.components.webview.FloatingToolbar
import uk.nktnet.webviewkiosk.ui.components.webview.WebviewAwareSwipeRefreshLayout
import uk.nktnet.webviewkiosk.ui.components.setting.BasicAuthDialog
import uk.nktnet.webviewkiosk.ui.components.webview.AddressBarSearchSuggestions
import uk.nktnet.webviewkiosk.ui.components.webview.LinkOptionsDialog
import uk.nktnet.webviewkiosk.utils.createCustomWebview
import uk.nktnet.webviewkiosk.utils.enterImmersiveMode
import uk.nktnet.webviewkiosk.utils.exitImmersiveMode
import uk.nktnet.webviewkiosk.utils.getMimeType
import uk.nktnet.webviewkiosk.utils.isSupportedFileURLMimeType
import uk.nktnet.webviewkiosk.utils.shouldBeImmersed
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.tryUnlockTask
import uk.nktnet.webviewkiosk.utils.unlockWithAuthIfRequired
import uk.nktnet.webviewkiosk.utils.webview.SchemeType
import uk.nktnet.webviewkiosk.utils.webview.SearchSuggestionEngine
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation
import uk.nktnet.webviewkiosk.utils.webview.getBlockInfo
import uk.nktnet.webviewkiosk.utils.webview.html.generateFileMissingPage
import uk.nktnet.webviewkiosk.utils.webview.html.generateUnsupportedMimeTypePage
import uk.nktnet.webviewkiosk.utils.webview.isCustomBlockPageUrl
import uk.nktnet.webviewkiosk.utils.webview.loadBlockedPage
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
    val scope = rememberCoroutineScope()
    var publishUrlVisitedJob: Job? = null

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
        if (userSettings.mqttEnabled && url.trimEnd('/') != systemSettings.currentUrl) {
            publishUrlVisitedJob?.cancel()
            publishUrlVisitedJob = scope.launch {
                delay(1000)
                MqttManager.publishUrlVisitedEvent(url)
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
            webView.requestFocus()
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(userSettings.webViewInset.toWindowInsets())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showAddressBar) {
                /**
                 * Wrap in AndroidView to avoid breaking autofill (e.g. Bitwarden/Proton Pass)
                 * in the WebView further below. Unsure why this is necessary.
                 */
                AndroidView(
                    factory = { ctx ->
                        ComposeView(ctx).apply {
                            setContent {
                                AddressBar(
                                    urlBarText = urlBarText,
                                    onUrlBarTextChange = { urlBarText = it },
                                    hasFocus = addressBarHasFocus,
                                    onFocusChanged = { focusState -> addressBarHasFocus = focusState.isFocused },
                                    addressBarSearch = addressBarSearch,
                                    customLoadUrl = ::customLoadUrl,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
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
                        tryLockTask(activity, showToast)
                    },
                    onUnlockClick = {
                        activity?.let {
                            focusManager.clearFocus()
                            unlockWithAuthIfRequired(activity, showToast)
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

    KioskControlPanel(navController, 10, ::customLoadUrl)

    BackPressHandler(::customLoadUrl)

    BasicAuthDialog(authHandler, authHost, authRealm) { authHandler = null }

    LinkOptionsDialog(
        link = linkToOpen,
        onDismiss = { linkToOpen = null },
        onOpenLink = { url -> customLoadUrl(url) },
    )

    LaunchedEffect(Unit) {
        MqttManager.commands.collect { commandMessage ->
            when (commandMessage) {
                is MqttGoBackMqttCommand -> WebViewNavigation.goBack(::customLoadUrl, systemSettings)
                is MqttGoForwardMqttCommand -> WebViewNavigation.goForward(::customLoadUrl, systemSettings)
                is MqttGoHomeMqttCommand -> WebViewNavigation.goHome(::customLoadUrl, systemSettings, userSettings)
                is MqttRefreshMqttCommand -> WebViewNavigation.refresh(::customLoadUrl, systemSettings, userSettings)
                is MqttGoToUrlMqttCommand -> customLoadUrl(commandMessage.data.url)
                is MqttLockMqttCommand -> tryLockTask(activity)
                is MqttUnlockMqttCommand -> tryUnlockTask(activity)
                is MqttErrorCommand -> showToast("Received invalid MQTT command. See debug logs in MQTT settings.")
                else -> Unit
            }
        }
    }
}
