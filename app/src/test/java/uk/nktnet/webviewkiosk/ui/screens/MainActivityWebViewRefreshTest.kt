package uk.nktnet.webviewkiosk.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.NavHostController
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.adblock.KotlinFilterEngine
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
import uk.nktnet.webviewkiosk.blocking.FilterListStore
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.HistoryEntry
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.OverrideUrlLoadingBlockActionOption
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager.RemoteMessage.Source
import uk.nktnet.webviewkiosk.utils.WEBVIEW_RECREATION_REQUEST_KEY
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen
import uk.nktnet.webviewkiosk.utils.webview.WebViewStartup
import uk.nktnet.webviewkiosk.utils.webview.WebViewStartupGate

/** Exercises the real MainActivity -> SetupNavHost -> WebviewScreen refresh path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityWebViewRefreshTest {
    private lateinit var activityController: ActivityController<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var view: ComposeView
    private lateinit var recomposer: Recomposer
    private var clock = BroadcastFrameClock()
    private var scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
    private var frameNanos = 0L
    private var documentRulesInstalled = false
    private var previousBlocker: ContentBlockingService? = null
    private lateinit var blockingSettings: AutoCloseable
    private val blockerField =
        ContentBlockingService::class.java.getDeclaredField("instance").apply {
            isAccessible = true
        }

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences("system_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        // Test navigation and WebView behaviour, not Robolectric's native Chromium
        // startup.
        // The real asynchronous completion/failure contract is tested separately.
        WebViewStartup.setGateForTests(
            WebViewStartupGate(beginStartup = { complete -> complete() })
        )
        UserSettings(context).apply {
            homeUrl = "about:blank"
            mqttEnabled = false
            enableJavaScript = false
            supportPdfRendering = false
            lockOnLaunch = false
            resetOnLaunch = false
            resetOnInactivitySeconds = 0
        }
        // Robolectric replaces the Application between methods, while this app
        // singleton retains its original store. Scope a real compiler/store to
        // this fixture and run its loader deterministically.
        previousBlocker = blockerField.get(null) as? ContentBlockingService
        okhttp3.OkHttp.initialize(context)
        val store = FilterListStore(context)
        store.remove(store.list().map { it.id }.toSet())
        val blocker =
            ContentBlockingService(
                store,
                KotlinFilterEngine(),
                Executor { it.run() },
            )
        blockingSettings = blocker.bindSettings(context)
        blockerField.set(null, blocker)
        activityController =
            Robolectric.buildActivity(MainActivity::class.java).create()
        activity = activityController.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        view = content.getChildAt(0) as ComposeView
        attachRecomposer()
        activityController.start().resume().visible()
        // A cold WebView is now mounted only after the async provider-startup
        // callback.
        // Never assert immediate native construction on the activity's first frame.
        awaitCondition {
            WebViewStartup.ready.value && mountedWebViews().size == 1
        }
        frames()
    }

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        WebViewStartup.setGateForTests(null)
        if (documentRulesInstalled) {
            val store = FilterListStore(activity)
            store.remove(store.list().map { it.id }.toSet())
            reloadDocumentRules()
        }
        blockingSettings.close()
        blockerField.set(null, previousBlocker)
    }

    @Test
    fun directLoadsAndHeaderLoadsApplyDocumentRulesBeforeLoading() {
        documentRules("||blocked.example^\$doc\n@@||blocked.example/safe\$document")
        val webView = webView()
        val before = shadowOf(webView).lastLoadedUrl
        webView.loadUrl("https://blocked.example/")
        assertBlockedDocument(webView, "https://blocked.example/")
        assertEquals(before, shadowOf(webView).lastLoadedUrl)
        webView.loadUrl("https://blocked.example/safe")
        assertEquals("https://blocked.example/safe", shadowOf(webView).lastLoadedUrl)
        webView.loadUrl("https://blocked.example/headers", mapOf("X-Test" to "kept"))
        assertBlockedDocument(webView, "https://blocked.example/headers")
        assertEquals("https://blocked.example/safe", shadowOf(webView).lastLoadedUrl)
        webView.loadUrl("https://allowed.example/", mapOf("X-Test" to "kept"))
        assertEquals(
            mapOf("X-Test" to "kept"),
            shadowOf(webView).lastAdditionalHttpHeaders,
        )
    }

    @Test
    fun appPostUsesPostMethodAndCustomBlockPageWithoutBecomingAGet() {
        documentRules("||forms.example^\$document,method=POST")
        val url = "https://forms.example/submit"
        val webView = webView()
        webView.loadUrl(url)
        assertEquals(url, shadowOf(webView).lastLoadedUrl)
        UserSettings(activity).customBlockPageHtml =
            "<html>Custom filter block</html>"
        webView.postUrl(url, "name=value".toByteArray())
        assertEquals(
            "<html>Custom filter block</html>",
            shadowOf(webView).lastLoadDataWithBaseURL.data,
        )
        assertEquals(url, shadowOf(webView).lastLoadDataWithBaseURL.baseUrl)
        assertEquals(url, shadowOf(webView).lastLoadedUrl)
    }

    @Test
    @Suppress("DEPRECATION")
    fun nativeNavigationUsesFrameMetadataAndLegacyRequestsAreIntercepted() {
        documentRules("||blocked.example^\$document\n@@||blocked.example/safe\$doc")
        val webView = webView()
        val client = webView.webViewClient
        assertTrue(
            client.shouldOverrideUrlLoading(
                webView,
                documentRequest("https://blocked.example/", redirect = true),
            )
        )
        assertBlockedDocument(webView, "https://blocked.example/")
        assertFalse(
            client.shouldOverrideUrlLoading(
                webView,
                documentRequest("https://blocked.example/safe", redirect = true),
            )
        )
        assertFalse(
            client.shouldOverrideUrlLoading(
                webView,
                documentRequest("https://blocked.example/frame", mainFrame = false),
            )
        )
        assertFalse(
            client.shouldOverrideUrlLoading(
                webView,
                "https://blocked.example/legacy",
            )
        )
        val legacy = "https://blocked.example/legacy"
        assertEquals(
            403,
            client
                .shouldInterceptRequest(
                    webView,
                    documentRequest(legacy),
                )!!
                .statusCode,
        )
        assertNull(
            client.shouldInterceptRequest(
                webView,
                documentRequest(legacy, mainFrame = false),
            )
        )
    }

    @Test
    fun nativeMainFrameInterceptionCoversPostReloadsAndKeepsSubframesSeparate() {
        documentRules("||forms.example^\$document,method=POST")
        val webView = webView()
        val client = webView.webViewClient
        val url = "https://forms.example/submit"
        webView.loadUrl(url)
        val reloads = shadowOf(webView).reloadInvocations
        webView.reload()
        assertEquals(reloads + 1, shadowOf(webView).reloadInvocations)
        val response =
            client.shouldInterceptRequest(
                webView,
                documentRequest(url, method = "POST"),
            )!!
        assertEquals(403, response.statusCode)
        assertEquals("no-store", response.responseHeaders["Cache-Control"])
        assertTrue(
            response.data
                .bufferedReader()
                .use { it.readText() }
                .contains("URL matches a content blocking filter")
        )
        assertNull(client.shouldInterceptRequest(webView, documentRequest(url)))
        assertNull(
            client.shouldInterceptRequest(
                webView,
                documentRequest(url, mainFrame = false, method = "POST"),
            )
        )
    }

    @Test
    fun nativeDocumentBlockingRespectsSilentActionAndGlobalDisable() {
        documentRules("||blocked.example^\$document")
        val webView = webView()
        val client = webView.webViewClient
        val before = shadowOf(webView).lastLoadDataWithBaseURL
        UserSettings(activity).overrideUrlLoadingBlockAction =
            OverrideUrlLoadingBlockActionOption.PREVENT_NAVIGATION
        assertTrue(
            client.shouldOverrideUrlLoading(
                webView,
                documentRequest("https://blocked.example/"),
            )
        )
        assertEquals(before, shadowOf(webView).lastLoadDataWithBaseURL)
        ContentBlockingService.get(activity).setEnabled(false)
        webView.loadUrl("https://blocked.example/")
        assertEquals("https://blocked.example/", shadowOf(webView).lastLoadedUrl)
        assertFalse(
            client.shouldOverrideUrlLoading(
                webView,
                documentRequest("https://blocked.example/next"),
            )
        )
    }

    @Test
    fun externalPdfWrapperIsCheckedAgainstItsOriginalDocumentUrl() {
        documentRules("||blocked.example/report.pdf\$doc")
        val source = "https://blocked.example/report.pdf"
        val wrapper =
            "${Constants.PDF_JS_ASSETS_DUMMY_URL}?wk_pdf_url=${Uri.encode(source)}"
        activityController.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse(wrapper)))
        frames()
        assertBlockedDocument(webView(), source)
        assertNull(webView().getTag(uk.nktnet.webviewkiosk.R.id.pdf_source_token))
    }

    private fun documentRules(text: String) {
        val store = FilterListStore(activity)
        store.remove(store.list().map { it.id }.toSet())
        store.importFrom("documents.txt", text.byteInputStream())
        documentRulesInstalled = true
        ContentBlockingService.get(activity).setEnabled(true)
        reloadDocumentRules()
        assertTrue(ContentBlockingService.get(activity).hasActiveRules)
    }

    private fun reloadDocumentRules() = runBlocking {
        val service = ContentBlockingService.get(activity)
        withTimeout(10_000) { service.awaitReload(service.reload()) }
    }

    private fun assertBlockedDocument(webView: WebView, url: String) {
        val loaded = shadowOf(webView).lastLoadDataWithBaseURL
        assertEquals(url, loaded.baseUrl)
        assertEquals("text/html", loaded.mimeType)
        assertTrue(loaded.data.contains("URL matches a content blocking filter"))
    }

    private fun documentRequest(
        url: String,
        mainFrame: Boolean = true,
        method: String = "GET",
        redirect: Boolean = false,
    ) =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame() = mainFrame

            override fun isRedirect() = redirect

            override fun hasGesture() = true

            override fun getMethod() = method

            override fun getRequestHeaders() = emptyMap<String, String>()
        }

    @Test
    fun acceptedExternalUrlRecreatesTheWebViewAndLoadsTheNewTarget() {
        val before = webView()
        val url = "https://example.invalid/external?revision=2"
        activityController.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        frames()
        val after = webView()

        assertNotSame(before, after)
        assertTrue(
            "replaced WebView must be destroyed",
            shadowOf(before).wasDestroyCalled(),
        )
        assertEquals(url, shadowOf(after).lastLoadedUrl)
        assertEquals("", SystemSettings(activity).intentUrl)
    }

    @Test
    fun appliedRemoteSettingsRecreateWebViewAndApplyItsUpdatedConfiguration() {
        val before = webView()
        assertTrue(!before.settings.javaScriptEnabled)
        UserSettings(activity).enableJavaScript = true
        // The process-wide flow dispatches asynchronously; wait for the activity
        // collector
        // before emitting rather than relying on frame scheduling order in the full
        // suite.
        awaitCondition {
            (RemoteMessageManager.settingsAppliedFlow as MutableSharedFlow<*>)
                .subscriptionCount
                .value > 0
        }
        RemoteMessageManager.emitSettingsApplied(
            InboundSettingsMessage(reloadActivity = true),
            Source.MQTT,
        )
        awaitCondition { webView() !== before }
        val after = webView()

        assertNotSame(before, after)
        assertTrue(
            "old WebView must be destroyed after settings reload",
            shadowOf(before).wasDestroyCalled(),
        )
        assertTrue(
            "reloaded settings must reach the replacement WebView",
            after.settings.javaScriptEnabled,
        )
        assertTrue(
            "the new WebView must remain in the view hierarchy",
            after.parent != null,
        )
    }

    @Test
    fun lateCallbacksFromRetiredWebViewCannotChangeCurrentHistory() {
        val before = webView()
        val oldClient = before.webViewClient
        val oldChrome = before.webChromeClient
        val currentUrl = "https://example.invalid/new-session"
        activityController.newIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
        )
        frames()
        val after = webView()
        assertNotSame(before, after)
        assertTrue(
            "retired WebView must be destroyed",
            shadowOf(before).wasDestroyCalled(),
        )
        assertEquals(currentUrl, shadowOf(after).lastLoadedUrl)

        val settings = SystemSettings(activity)
        val currentHistory = listOf(HistoryEntry(url = currentUrl))
        settings.historyStack = currentHistory
        settings.historyIndex = 0
        oldClient.onPageStarted(before, "https://example.invalid/retired", null)
        oldClient.doUpdateVisitedHistory(
            before,
            "https://example.invalid/retired",
            false,
        )
        oldClient.onPageFinished(before, "https://example.invalid/retired")
        oldChrome?.onProgressChanged(before, 5)
        frames()

        assertEquals(currentHistory, settings.historyStack)
        assertEquals(0, settings.historyIndex)
        assertEquals(currentUrl, shadowOf(after).lastLoadedUrl)
    }

    @Test
    fun recreatingActivityRemountsItsWebViewAndRetainsNavigationState() {
        val before = webView()
        activityController.newIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/restore"))
        )
        frames()
        val loaded = webView()
        assertNotSame(before, loaded)
        val oldEntry = controller().getBackStackEntry(Screen.WebView.route)
        assertEquals(
            1,
            oldEntry.savedStateHandle.get<Int>(WEBVIEW_RECREATION_REQUEST_KEY),
        )
        val oldView = view
        val oldScope = scope
        val oldRecomposer = recomposer

        activityController = activityController.recreate()
        oldView.disposeComposition()
        oldRecomposer.cancel()
        oldScope.cancel()
        activity = activityController.get()
        view =
            activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                as ComposeView
        clock = BroadcastFrameClock()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
        frameNanos = 0L
        attachRecomposer()
        frames()

        val restored = webView()
        assertNotSame(loaded, restored)
        assertTrue(restored.parent != null)
        assertEquals("", SystemSettings(activity).intentUrl)
        val restoredController = controller()
        val restoredEntry =
            restoredController.getBackStackEntry(Screen.WebView.route)
        assertEquals(
            Screen.WebView.route,
            restoredController.currentDestination?.route,
        )
        assertEquals(
            1,
            restoredController.currentBackStack.value.count {
                it.destination.route == Screen.WebView.route
            },
        )
        val priorRefreshCount =
            restoredEntry.savedStateHandle.get<Int>(WEBVIEW_RECREATION_REQUEST_KEY)
                ?: 0
        navigateToWebViewScreen(restoredController)
        frames()
        assertEquals(
            priorRefreshCount + 1,
            restoredEntry.savedStateHandle.get<Int>(WEBVIEW_RECREATION_REQUEST_KEY),
        )
    }

    private fun controller(): NavHostController =
        MainActivity::class
            .java
            .getDeclaredField("navController")
            .apply { isAccessible = true }
            .get(activity) as NavHostController

    private fun attachRecomposer() {
        recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view.setParentCompositionContext(recomposer)
    }

    private fun webView(): WebView {
        val found = mountedWebViews()
        assertEquals("expected exactly one mounted WebView", 1, found.size)
        return found.single()
    }

    private fun mountedWebViews(): List<WebView> {
        val found = mutableListOf<WebView>()
        fun collect(view: View) {
            if (view is WebView) {
                found.add(view)
            }
            if (view is ViewGroup) {
                (0 until view.childCount).forEach { collect(view.getChildAt(it)) }
            }
        }
        collect(view)
        return found
    }

    private fun awaitCondition(condition: () -> Boolean) {
        repeat(100) {
            frames()
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        assertTrue(
            "Timed out waiting for asynchronously delivered settings",
            condition(),
        )
    }

    private fun frames() {
        repeat(12) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            frameNanos += 16_000_000L
            clock.sendFrame(frameNanos)
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
        }
    }
}
