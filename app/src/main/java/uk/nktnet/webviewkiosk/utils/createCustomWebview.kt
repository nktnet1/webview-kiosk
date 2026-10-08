package uk.nktnet.webviewkiosk.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ClientCertRequest
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.data.WebViewCreation
import uk.nktnet.webviewkiosk.config.option.OverrideUrlLoadingBlockActionOption
import uk.nktnet.webviewkiosk.config.option.SslErrorModeOption
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.managers.PdfJsManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.utils.webview.HttpAuthRequest
import uk.nktnet.webviewkiosk.utils.webview.NfcBridgeManager
import uk.nktnet.webviewkiosk.utils.webview.SchemeType
import uk.nktnet.webviewkiosk.utils.webview.WebViewDialogController
import uk.nktnet.webviewkiosk.utils.webview.getBlockInfo
import uk.nktnet.webviewkiosk.utils.webview.handleMutualTlsRequest
import uk.nktnet.webviewkiosk.utils.webview.handlers.handleDownloadPrompt
import uk.nktnet.webviewkiosk.utils.webview.handlers.handleGeolocationRequest
import uk.nktnet.webviewkiosk.utils.webview.handlers.handlePdfSourceRequest
import uk.nktnet.webviewkiosk.utils.webview.handlers.handlePermissionRequest
import uk.nktnet.webviewkiosk.utils.webview.handlers.handleSslErrorPromptRequest
import uk.nktnet.webviewkiosk.utils.webview.handlers.isPdfSourceNavigation
import uk.nktnet.webviewkiosk.utils.webview.interfaces.BatteryInterface
import uk.nktnet.webviewkiosk.utils.webview.interfaces.BlobInterface
import uk.nktnet.webviewkiosk.utils.webview.interfaces.BrightnessInterface
import uk.nktnet.webviewkiosk.utils.webview.interfaces.NfcInterface
import uk.nktnet.webviewkiosk.utils.webview.isCustomBlockPageUrl
import uk.nktnet.webviewkiosk.utils.webview.isPdfViewerUrl
import uk.nktnet.webviewkiosk.utils.webview.loadBlockedPage
import uk.nktnet.webviewkiosk.utils.webview.parseFileChooserResult
import uk.nktnet.webviewkiosk.utils.webview.parseMutualTlsRules
import uk.nktnet.webviewkiosk.utils.webview.resolveBlockPageUrl
import uk.nktnet.webviewkiosk.utils.webview.scripts.generateDarkReaderScript
import uk.nktnet.webviewkiosk.utils.webview.scripts.generateDesktopViewportScript
import uk.nktnet.webviewkiosk.utils.webview.scripts.generateDisableVibrationApiScript
import uk.nktnet.webviewkiosk.utils.webview.scripts.generateErudaConsoleScript
import uk.nktnet.webviewkiosk.utils.webview.scripts.generatePrefersColorSchemeOverrideScript
import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE
import java.io.File

private const val LOCAL_NETWORK_PERMISSION_ERROR = "ERR_LOCAL_NETWORK_PERMISSION_MISSING"

private fun isLocalNetworkPermissionError(error: WebResourceErrorCompat?): Boolean {
    return (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
        && error?.description?.toString()?.contains(LOCAL_NETWORK_PERMISSION_ERROR) == true
    )
}

private fun isWebPdf(
    url: String,
    contentDisposition: String?,
    mimeType: String?
): Boolean {
    val uri = runCatching { url.toUri() }.getOrNull() ?: return false
    if (uri.scheme != "http" && uri.scheme != "https") {
        return false
    }

    val normalizedMimeType = mimeType
        ?.substringBefore(';')
        ?.trim()

    return normalizedMimeType.equals("application/pdf", ignoreCase = true) || URLUtil.guessFileName(
        url,
        contentDisposition,
        mimeType
    ).endsWith(".pdf", ignoreCase = true)
}

data class WebViewConfig(
    val systemSettings: SystemSettings,
    val userSettings: UserSettings,
    val blacklistRegexes: List<Regex>,
    val whitelistRegexes: List<Regex>,
    val setLastErrorUrl: (errorUrl: String) -> Unit,
    val onLocalNetworkPermissionMissing: () -> Unit,
    val finishSwipeRefresh: () -> Unit,
    val onProgressChanged: (newProgress: Int) -> Unit,
    val updateAddressBarAndHistory: (url: String, originalUrl: String?) -> Unit,
    val onHttpAuthRequest: (request: HttpAuthRequest?) -> Unit,
    val onLinkLongClick: (url: String) -> Unit,
    val onImageLongClick: (url: String) -> Unit,
    val onPdfUrlRequested: (webView: WebView, url: String) -> Unit,
    val onRenderProcessGone: () -> Unit,
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun createCustomWebview(
    context: Context,
    config: WebViewConfig,
    recreationKey: Int = 0
): WebViewCreation {
    val systemSettings = config.systemSettings
    val userSettings = config.userSettings
    val scope = rememberCoroutineScope()

    var pendingFileChooserCallback by remember {
        mutableStateOf<ValueCallback<Array<Uri>>?>(null)
    }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileChooserOwner by remember { mutableStateOf<WebView?>(null) }
    var pendingFileChooserInFlight by rememberSaveable { mutableStateOf(false) }

    fun respondFileChooser(callback: ValueCallback<Array<Uri>>?, uris: Array<Uri>?) {
        try {
            callback?.onReceiveValue(uris)
        } catch (e: Exception) {
            Log.w(Constants.APP_SCHEME, "Unable to complete WebView file chooser request", e)
        }
    }

    fun completeFileChooser(uris: Array<Uri>?, owner: WebView? = null) {
        if (owner != null && pendingFileChooserOwner !== owner) return
        val callback = pendingFileChooserCallback
        pendingFileChooserCallback = null
        pendingCaptureUri = null
        pendingFileChooserOwner = null
        // An abandoned launch stays busy until its result arrives, so it cannot reach a new page.
        respondFileChooser(callback, uris)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pendingFileChooserInFlight = false
        completeFileChooser(
            if (pendingFileChooserCallback != null) {
                parseFileChooserResult(context, result.resultCode, result.data)
            } else null
        )
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pendingFileChooserInFlight = false
        completeFileChooser(
            if (pendingFileChooserCallback != null) {
                parseFileChooserResult(context, result.resultCode, result.data, pendingCaptureUri)
            } else null
        )
    }

    fun launchFilePicker(fileChooserParams: WebChromeClient.FileChooserParams) {
        runCatching {
            val intent = fileChooserParams.createIntent()
            if (fileChooserParams.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pendingFileChooserInFlight = true
            filePickerLauncher.launch(intent)
        }.onFailure {
            pendingFileChooserInFlight = false
            Log.e(Constants.APP_SCHEME, "Failed to launch file picker", it)
            completeFileChooser(null)
            ToastManager.show(context, "Unable to open file picker.")
        }
    }

    fun customLaunchCapture(action: String, fileSuffix: String): Boolean {
        runCatching {
            val captureDir = File(
                context.cacheDir,
                Constants.FILE_CAPTURE_CACHE_PATH_NAME
            ).apply {
                mkdirs()
            }
            val outputFile = File.createTempFile(
                "${Constants.FILE_CAPTURE_CACHE_PATH_NAME}_",
                fileSuffix,
                captureDir
            )
            val outputUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                outputFile
            )
            pendingCaptureUri = outputUri
            val captureIntent = Intent(action).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            if (captureIntent.resolveActivity(context.packageManager) != null) {
                pendingFileChooserInFlight = true
                captureLauncher.launch(captureIntent)
                return true
            } else {
                Log.w(Constants.APP_SCHEME, "No activity available for capture action: $action")
            }
        }.onFailure {
            pendingFileChooserInFlight = false
            Log.e(Constants.APP_SCHEME, "Failed to launch capture action: $action", it)
        }
        // Keep the callback pending so the file picker fallback can complete the upload.
        pendingCaptureUri = null
        return false
    }

    fun buildWebView(dialogs: WebViewDialogController): WebViewCreation.Success {
        val blobInterface = if (userSettings.allowFileDownload) {
            BlobInterface(context)
        } else {
            null
        }
        var hideFullscreen: () -> Unit = {}
        var disposed = false

        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val localFileAssetLoader = if (userSettings.allowLocalFiles) {
                WebViewAssetLoader.Builder()
                    .addPathHandler(
                        "/${Constants.WEB_CONTENT_FILES_DIR}/",
                        WebViewAssetLoader.InternalStoragePathHandler(
                            context,
                            getWebContentFilesDir(context)
                        )
                    )
                    .build()
            } else {
                null
            }

            val pdfAssetLoader = if (userSettings.supportPdfRendering) {
                WebViewAssetLoader.Builder()
                    .setDomain(Constants.PDF_JS_ASSETS_DUMMY_URL.toUri().host ?: "")
                    .addPathHandler(
                        "/pdfjs_local/",
                        WebViewAssetLoader.InternalStoragePathHandler(
                            context,
                            File(
                                context.filesDir,
                                Constants.PDF_JS_ASSETS_DIR
                            )
                        )
                    )
                    .build()
            } else {
                null
            }

            settings.apply {
                javaScriptEnabled = userSettings.enableJavaScript
                domStorageEnabled = userSettings.enableDomStorage
                cacheMode = userSettings.cacheMode.mode
                userAgentString = userSettings.userAgent.takeIf { it.isNotBlank() }
                    ?: settings.userAgentString
                layoutAlgorithm = userSettings.layoutAlgorithm.algorithm
                useWideViewPort = userSettings.useWideViewport
                loadWithOverviewMode = userSettings.loadWithOverviewMode

                setGeolocationEnabled(userSettings.allowLocation)
                setInitialScale(userSettings.initialScale)
                setSupportZoom(userSettings.supportZoom)

                builtInZoomControls = userSettings.builtInZoomControls
                displayZoomControls = userSettings.displayZoomControls

                allowFileAccess = userSettings.allowLocalFiles
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = userSettings.allowFileAccessFromFileURLs
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs =
                    userSettings.allowUniversalAccessFromFileURLs
                mediaPlaybackRequiresUserGesture =
                    userSettings.mediaPlaybackRequiresUserGesture

                mixedContentMode = userSettings.mixedContentMode.mode
                overScrollMode = userSettings.overScrollMode.mode
            }

            if (userSettings.enableBatteryApi) {
                addJavascriptInterface(BatteryInterface(context), BatteryInterface.NAME)
            }
            if (userSettings.enableBrightnessApi) {
                addJavascriptInterface(BrightnessInterface(context), BrightnessInterface.NAME)
            }
            if (userSettings.allowNfc) {
                addJavascriptInterface(NfcInterface(context), NfcInterface.NAME)
                NfcBridgeManager.attachWebView(this)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    runCatching {
                        WebViewCompat.addDocumentStartJavaScript(
                            this,
                            NfcInterface.JS_WEB_NFC_HOOK,
                            setOf("*")
                        )
                    }
                }
            }
            if (
                !userSettings.enableVibrationApi
                && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            ) {
                runCatching {
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        generateDisableVibrationApiScript(),
                        setOf("*")
                    )
                }
            }
            blobInterface?.let {
                addJavascriptInterface(it, BlobInterface.NAME)
            }

            val requestUserAgent = settings.userAgentString
            fun isCurrentPdfSource(token: String): Boolean =
                !disposed && getTag(R.id.pdf_source_token) == token

            webViewClient = object : WebViewClientCompat() {
                override fun onReceivedClientCertRequest(
                    view: WebView?,
                    request: ClientCertRequest?
                ) {
                    if (request == null) return
                    handleMutualTlsRequest(
                        activity = context as? Activity,
                        context = context,
                        request = request,
                        siteRules = parseMutualTlsRules(
                            userSettings.mutualTls
                        ).orEmpty(),
                        systemSettings = systemSettings,
                        scope = scope,
                    )
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    val pdfToken = getTag(R.id.pdf_source_token) as? String
                    val isCurrentPdfNavigation = isPdfSourceNavigation(pdfToken, url)
                    if (
                        url != null
                        && isPdfViewerUrl(url.toUri())
                        && !isCurrentPdfNavigation
                    ) {
                        // loadDataWithBaseURL queues page-start callbacks. An older viewer
                        // must not clear the newer viewer's token, authentication, or errors.
                        return
                    }
                    config.setLastErrorUrl("")
                    config.onHttpAuthRequest(null)
                    view?.let { completeFileChooser(null, it) }
                    if (!isCurrentPdfNavigation) {
                        setTag(R.id.pdf_source_token, null)
                    }
                    blobInterface?.abortAllDownloads()
                    NfcBridgeManager.resetSession()
                    if (userSettings.requestFocusOnPageStart) {
                        runCatching {
                            view?.requestFocus()
                        }
                    }
                    if (userSettings.applyAppTheme && userSettings.theme != ThemeOption.SYSTEM) {
                        evaluateJavascript(
                            generatePrefersColorSchemeOverrideScript(userSettings.theme),
                            null
                        )
                    }
                    if (userSettings.allowFileDownload) {
                        view?.evaluateJavascript(BlobInterface.JS_BLOB_HOOK, null)
                    }
                    if (userSettings.customScriptOnPageStart.isNotBlank()) {
                        view?.evaluateJavascript(
                            wrapJsInIIFE(userSettings.customScriptOnPageStart),
                            null
                        )
                    }
                    if (userSettings.allowNfc) {
                        view?.evaluateJavascript(NfcInterface.JS_WEB_NFC_HOOK, null)
                    }
                    if (!userSettings.enableVibrationApi) {
                        view?.evaluateJavascript(generateDisableVibrationApiScript(), null)
                    }
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    config.finishSwipeRefresh()

                    url?.let {
                        /*
                         * [URL_BEFORE_NAVIGATION] reset when loaded - must check
                         * progress = 100 due to webview bug where onPageFinished
                         * gets called multiple times.
                         * https://issuetracker.google.com/issues/36983315
                         */
                        if (progress == 100) {
                            if (
                                userSettings.applyDesktopViewportWidth >= Constants.MIN_DESKTOP_WIDTH
                            ) {
                                view?.evaluateJavascript(
                                    generateDesktopViewportScript(
                                        userSettings.applyDesktopViewportWidth
                                    ),
                                    null,
                                )
                            }
                            if (userSettings.enableDarkReader) {
                                view?.evaluateJavascript(
                                    generateDarkReaderScript(
                                        context.getString(R.string.app_name)
                                    ),
                                    null,
                                )
                            }
                            if (userSettings.enableErudaConsole) {
                                view?.evaluateJavascript(
                                    generateErudaConsoleScript(
                                        context.getString(R.string.app_name)
                                    ),
                                    null,
                                )
                            }
                            if (userSettings.customScriptOnPageFinish.isNotBlank()) {
                                view?.evaluateJavascript(
                                    wrapJsInIIFE(userSettings.customScriptOnPageFinish),
                                    null,
                                )
                            }
                            systemSettings.urlBeforeNavigation = ""
                        }
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (request != null) {
                        handlePdfSourceRequest(
                            context,
                            request,
                            requestUserAgent,
                            userSettings,
                            config.blacklistRegexes,
                            config.whitelistRegexes,
                            onAuthenticationRequired = { token, _, authRequest ->
                                if (!post {
                                    if (isCurrentPdfSource(token)) {
                                        config.onHttpAuthRequest(authRequest)
                                    } else {
                                        authRequest.cancel()
                                    }
                                }) authRequest.cancel()
                            },
                            onAuthenticated = { token, sourceUrl ->
                                post {
                                    if (isCurrentPdfSource(token)) {
                                        config.onPdfUrlRequested(this@apply, sourceUrl)
                                    }
                                }
                            },
                            onViewerEvent = { token, sourceUrl, event ->
                                post {
                                    if (isCurrentPdfSource(token)) {
                                        val targetUrl = sourceUrl.ifEmpty { systemSettings.currentUrl }
                                        when (event) {
                                            "error" -> config.setLastErrorUrl(targetUrl)
                                            "loaded", "authentication" -> config.setLastErrorUrl("")
                                            "retry" -> config.onPdfUrlRequested(this@apply, targetUrl)
                                        }
                                    }
                                }
                            },
                        )?.let {
                            return it
                        }

                        if (localFileAssetLoader != null) {
                            val response = localFileAssetLoader.shouldInterceptRequest(request.url)
                            if (response != null) {
                                return response
                            }
                        }

                        if (pdfAssetLoader != null) {
                            val response = pdfAssetLoader.shouldInterceptRequest(request.url)
                            if (response != null) {
                                return response
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    return handleUrlLoading(
                        view, request.url.toString(), request.isForMainFrame
                    )
                }

                @Deprecated("For API < 24")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // Older WebViews without the compatibility callback omit frame metadata.
                    // Only HTTP(S) overrides are guaranteed to target the main frame.
                    val isMainFrame = url?.toUri()?.scheme?.lowercase() in setOf("http", "https")
                    return handleUrlLoading(view, url, isMainFrame)
                }

                private fun handleUrlLoading(
                    view: WebView?,
                    requestUrl: String?,
                    isMainFrame: Boolean,
                ): Boolean {
                    if (requestUrl.isNullOrEmpty()) {
                        return false
                    }
                    val navigationUrl = resolveBlockPageUrl(
                        requestUrl, config.blacklistRegexes, config.whitelistRegexes, userSettings
                    ) ?: return true
                    if (isMainFrame) {
                        systemSettings.urlBeingHandled = navigationUrl
                        if (systemSettings.urlBeforeNavigation.isEmpty()) {
                            // [URL_BEFORE_NAVIGATION] first to run for native navigation (non-SPA)
                            systemSettings.urlBeforeNavigation = systemSettings.currentUrl
                        }
                    }

                    val (schemeType, blockCause) = getBlockInfo(
                        url = navigationUrl,
                        blacklistRegexes = config.blacklistRegexes,
                        whitelistRegexes = config.whitelistRegexes,
                        userSettings = userSettings
                    )
                    if (blockCause != null) {
                        when (userSettings.overrideUrlLoadingBlockAction) {
                            OverrideUrlLoadingBlockActionOption.SHOW_BLOCK_PAGE -> {
                                if (isMainFrame) {
                                    loadBlockedPage(
                                        view,
                                        userSettings,
                                        navigationUrl,
                                        blockCause,
                                    )
                                }
                            }

                            OverrideUrlLoadingBlockActionOption.SHOW_TOAST -> {
                                ToastManager.show(context, userSettings.blockedMessage)
                            }

                            else -> Unit
                        }
                        return true
                    }

                    if (schemeType == SchemeType.OTHER) {
                        if (userSettings.allowOtherUrlSchemes) {
                            handleExternalSchemeUrl(context, navigationUrl)
                        }
                        return true
                    }

                    if (
                        isMainFrame
                        && view != null
                        && userSettings.supportPdfRendering
                        && PdfJsManager.areAssetsReady(context)
                        && isWebPdf(navigationUrl, null, null)
                    ) {
                        config.onPdfUrlRequested(view, navigationUrl)
                        return true
                    }
                    if (navigationUrl != requestUrl) {
                        if (isMainFrame) loadUrl(navigationUrl)
                        return true
                    }
                    return false
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean
                ) {
                    if (url == null) {
                        return
                    }
                    val isPdfViewer = isPdfViewerUrl(url.toUri())
                    val navigationUrl = if (isPdfViewer) {
                        resolveBlockPageUrl(
                            url, config.blacklistRegexes, config.whitelistRegexes, userSettings
                        ) ?: return
                    } else {
                        url
                    }
                    val historyOriginalUrl = if (isPdfViewer) navigationUrl else originalUrl
                    if (
                        !isPdfViewer
                        && systemSettings.urlBeingHandled.trimEnd('/') == navigationUrl.trimEnd('/')
                    ) {
                        config.updateAddressBarAndHistory(navigationUrl, historyOriginalUrl)
                        return
                    }

                    /**
                     * This section of the code is only ever reached if either customLoadUrl or
                     * shouldOverrideUrlLoading was not triggered, e.g. during JS navigation in
                     * Single Page Applications (e.g. a React SPA).
                     */
                    if (systemSettings.urlBeforeNavigation.isEmpty()) {
                        systemSettings.urlBeforeNavigation = systemSettings.currentUrl
                    }

                    systemSettings.urlBeingHandled = navigationUrl

                    val (schemeType, blockCause) = getBlockInfo(
                        url = navigationUrl,
                        blacklistRegexes = config.blacklistRegexes,
                        whitelistRegexes = config.whitelistRegexes,
                        userSettings = userSettings
                    )

                    val uri = navigationUrl.toUri()
                    if (isCustomBlockPageUrl(schemeType, uri)) {
                        // Already on custom block page.
                        val blockUrl = uri.getQueryParameter("url")
                        blockUrl?.let {
                            config.updateAddressBarAndHistory(blockUrl, historyOriginalUrl)
                        }
                        return
                    }

                    if (blockCause != null) {
                        loadBlockedPage(
                            view,
                            userSettings,
                            navigationUrl,
                            blockCause,
                        )
                        config.updateAddressBarAndHistory(navigationUrl, historyOriginalUrl)
                        return
                    }
                    if (schemeType == SchemeType.OTHER) {
                        return
                    }
                    config.updateAddressBarAndHistory(navigationUrl, historyOriginalUrl)
                }

                override fun onReceivedHttpAuthRequest(
                    view: WebView?,
                    handler: HttpAuthHandler?,
                    host: String?,
                    realm: String?
                ) {
                    if (handler == null) return
                    if (disposed) {
                        handler.cancel()
                        return
                    }
                    config.onHttpAuthRequest(HttpAuthRequest(
                        host, realm,
                        onSubmit = { username, password -> handler.proceed(username, password) },
                        onCancel = { handler.cancel() }
                    ))
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceErrorCompat
                ) {
                    if (isLocalNetworkPermissionError(error)) {
                        config.onLocalNetworkPermissionMissing()
                    }
                    if (request.isForMainFrame) {
                        config.setLastErrorUrl(request.url.toString())
                        return
                    }
                    super.onReceivedError(view, request, error)
                }

                @Suppress("DeprecatedCallableAddReplaceWith")
                @Deprecated("For API < 23")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && !failingUrl.isNullOrEmpty()) {
                        config.setLastErrorUrl(failingUrl)
                    }
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    when (userSettings.sslErrorMode) {
                        SslErrorModeOption.BLOCK -> handler?.cancel()
                        SslErrorModeOption.PROMPT -> handleSslErrorPromptRequest(
                            context, handler, error, dialogs
                        )

                        SslErrorModeOption.PROCEED -> handler?.proceed()
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {
                    Log.e(
                        Constants.APP_SCHEME,
                        "WebView renderer gone. crashed=${detail}"
                    )
                    hideFullscreen()
                    disposed = true
                    completeFileChooser(null, view)
                    config.onHttpAuthRequest(null)
                    dialogs.dispose()
                    (view.parent as? ViewGroup)?.removeView(view)
                    NfcBridgeManager.detachWebView(view)
                    blobInterface?.dispose()
                    view.destroy()
                    config.onRenderProcessGone()
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                private var customView: View? = null
                private var customViewCallback: CustomViewCallback? = null
                private var fullScreenContainer: FrameLayout? = null

                init {
                    hideFullscreen = ::onHideCustomView
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    config.onProgressChanged(newProgress)
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    handlePermissionRequest(context, request, systemSettings, userSettings, dialogs)
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    dialogs.dismiss(request, resolveRequest = false)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    origin?.let {
                        handleGeolocationRequest(
                            context,
                            it.trimEnd('/'),
                            callback,
                            systemSettings,
                            userSettings,
                            dialogs,
                        )
                    }
                }

                override fun onGeolocationPermissionsHidePrompt() {
                    dialogs.dismiss(WebViewDialogController.GEOLOCATION_PROMPT, resolveRequest = false)
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (customView != null) {
                        callback.onCustomViewHidden()
                        return
                    }

                    val activity = context as? Activity
                    if (activity == null) {
                        callback.onCustomViewHidden()
                        return
                    }
                    fullScreenContainer = FrameLayout(activity).apply {
                        addView(
                            view,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    customView = view
                    customViewCallback = callback

                    activity.addContentView(
                        fullScreenContainer,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )

                    enterImmersiveMode(activity)

                    visibility = View.GONE
                    fullScreenContainer?.visibility = View.VISIBLE
                }

                override fun onHideCustomView() {
                    val activity = context as? Activity
                    val container = fullScreenContainer
                    val callback = customViewCallback
                    val view = customView
                    if (container == null && callback == null && view == null) {
                        return
                    }
                    fullScreenContainer = null
                    customView = null
                    customViewCallback = null

                    container?.removeView(view)
                    (container?.parent as? ViewGroup)?.removeView(container)
                    visibility = View.VISIBLE

                    activity?.let {
                        val shouldExit = !shouldBeImmersed(activity, userSettings)
                        if (shouldExit) {
                            exitImmersiveMode(it)
                        }
                    }
                    try {
                        callback?.onCustomViewHidden()
                    } catch (e: Exception) {
                        Log.w(Constants.APP_SCHEME, "Unable to complete fullscreen cleanup", e)
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    if (disposed || pendingFileChooserInFlight || pendingFileChooserCallback != null) {
                        respondFileChooser(filePathCallback, null)
                        return true
                    }
                    if (!config.userSettings.allowFilePicker) {
                        ToastManager.show(
                            context,
                            "File picker is disabled in ${context.getString(R.string.app_name)}'s Web Engine settings."
                        )
                        respondFileChooser(filePathCallback, null)
                        return true
                    }

                    pendingFileChooserCallback = filePathCallback
                    pendingFileChooserOwner = webView

                    val acceptTypes = fileChooserParams.acceptTypes.filter { it.isNotBlank() }
                    val wantsImage = acceptTypes.any { it.startsWith("image/") }
                    val wantsVideo = acceptTypes.any { it.startsWith("video/") }
                    val wantsAudio = acceptTypes.any { it.startsWith("audio/") }

                    val hasCameraPermission =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED

                    val hasAudioPermission =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED

                    Log.d(
                        Constants.APP_SCHEME,
                        "File chooser request: mode=${fileChooserParams.mode}, "
                            + "capture=${fileChooserParams.isCaptureEnabled}, acceptTypes=$acceptTypes, "
                            + "allowCamera=${config.userSettings.allowCamera}, cameraPermission=$hasCameraPermission, "
                            + "allowMicrophone=${config.userSettings.allowMicrophone}, audioPermission=$hasAudioPermission"
                    )

                    val captureRequest = when {
                        !fileChooserParams.isCaptureEnabled -> null
                        (
                            wantsImage
                            && config.userSettings.allowCamera
                            && hasCameraPermission
                        ) -> {
                            MediaStore.ACTION_IMAGE_CAPTURE to ".jpg"
                        }
                        (
                            wantsVideo
                            && config.userSettings.allowCamera
                            && hasCameraPermission
                        ) -> {
                            MediaStore.ACTION_VIDEO_CAPTURE to ".mp4"
                        }
                        (
                            wantsAudio
                            && config.userSettings.allowMicrophone
                            && hasAudioPermission
                        ) -> {
                            MediaStore.Audio.Media.RECORD_SOUND_ACTION to ".m4a"
                        }
                        else -> {
                            null
                        }
                    }

                    val captureHandled = (
                        captureRequest != null
                        && customLaunchCapture(
                            captureRequest.first,
                            captureRequest.second,
                        )
                    )

                    if (!captureHandled) {
                        launchFilePicker(fileChooserParams)
                    }
                    return true
                }
            }

            setOnLongClickListener {
                val result = hitTestResult
                if (
                    userSettings.allowLinkLongPressContextMenu
                    && (
                        result.type == WebView.HitTestResult.IMAGE_TYPE
                        || result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                        || result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                    )
                ) {
                    when (result.type) {
                        WebView.HitTestResult.IMAGE_TYPE,
                        WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                            result.extra?.let { imageUrl ->
                                config.onImageLongClick(imageUrl)
                            }
                            return@setOnLongClickListener true
                        }
                        WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                            result.extra?.let { link ->
                                config.onLinkLongClick(link)
                            }
                            return@setOnLongClickListener true
                        }
                    }
                }
                !userSettings.allowDefaultLongPress
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                if (
                    userSettings.supportPdfRendering
                    && PdfJsManager.areAssetsReady(context)
                    && isWebPdf(url, contentDisposition, mimeType)
                ) {
                    config.onPdfUrlRequested(this, url)
                } else {
                    handleDownloadPrompt(
                        context = context,
                        webView = this,
                        url = url,
                        userAgent = userAgent,
                        contentDisposition = contentDisposition,
                        mimeType = mimeType,
                        dialogs = dialogs,
                    )
                }
            }
        }

        return WebViewCreation.Success(webView) {
            hideFullscreen()
            disposed = true
            completeFileChooser(null, webView)
            config.onHttpAuthRequest(null)
            dialogs.dispose()
            blobInterface?.dispose()
        }
    }

    val webViewCreationResult = remember(recreationKey) {
        val dialogs = WebViewDialogController(context)
        try {
            buildWebView(dialogs)
        } catch (e: Exception) {
            dialogs.dispose()
            Log.e(Constants.APP_SCHEME, "Failed to create WebView", e)
            WebViewCreation.Failure(e)
        }
    }

    return webViewCreationResult
}
