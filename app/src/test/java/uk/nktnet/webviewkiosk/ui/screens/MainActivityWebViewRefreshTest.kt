@file:Suppress("UsePropertyAccessSyntax")

package uk.nktnet.webviewkiosk.ui.screens

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.NavHostController
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
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
import uk.nktnet.webviewkiosk.config.option.ImmersiveModeOption
import uk.nktnet.webviewkiosk.config.option.OverrideUrlLoadingBlockActionOption
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.managers.PdfJsManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager.RemoteMessage.Source
import uk.nktnet.webviewkiosk.utils.WEBVIEW_RECREATION_REQUEST_KEY
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen
import uk.nktnet.webviewkiosk.utils.webview.WebViewStartup
import uk.nktnet.webviewkiosk.utils.webview.WebViewStartupGate

/** Exercises the real MainActivity -> SetupNavHost -> WebviewScreen refresh path. */
@Suppress("UsePropertyAccessSyntax")
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
        shadowOf(context)
            .grantPermissions(
                "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
            )
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
            ContentBlockingService(store, KotlinFilterEngine()) { it.run() }
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

    @Suppress("DEPRECATION")
    @Test
    fun regainingWindowFocusRestoresStickyImmersiveBarsOnWebView() {
        UserSettings(activity).immersiveMode = ImmersiveModeOption.ALWAYS_ON
        val decorView = activity.window.decorView
        val hiddenBars =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        // Simulate another activity clearing immersive flags while the Compose
        // destination stays mounted; losing focus must not hide the bars.
        decorView.systemUiVisibility =
            decorView.systemUiVisibility and hiddenBars.inv()
        activityController.windowFocusChanged(false)
        assertEquals(0, decorView.systemUiVisibility and hiddenBars)

        activityController.windowFocusChanged(true)
        assertEquals(hiddenBars, decorView.systemUiVisibility and hiddenBars)
    }

    @Suppress("DEPRECATION")
    @Test
    fun focusRecoveryDoesNotHideBarsWhenImmersiveModeIsDisabled() {
        UserSettings(activity).immersiveMode = ImmersiveModeOption.ALWAYS_OFF
        val decorView = activity.window.decorView
        val hiddenBars =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        decorView.systemUiVisibility =
            decorView.systemUiVisibility and hiddenBars.inv()

        activityController.windowFocusChanged(false)
        activityController.windowFocusChanged(true)
        assertEquals(0, decorView.systemUiVisibility and hiddenBars)
    }

    @Suppress("DEPRECATION")
    @Test
    fun focusRecoveryDoesNotHideBarsOnInAppSettingsScreen() {
        UserSettings(activity).immersiveMode = ImmersiveModeOption.ALWAYS_ON
        controller().navigate(Screen.Settings.route)
        frames()
        val decorView = activity.window.decorView
        val hiddenBars =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        decorView.systemUiVisibility =
            decorView.systemUiVisibility and hiddenBars.inv()

        activityController.windowFocusChanged(false)
        activityController.windowFocusChanged(true)
        assertEquals(Screen.Settings.route, controller().currentDestination?.route)
        assertEquals(0, decorView.systemUiVisibility and hiddenBars)
    }

    @Suppress("DEPRECATION")
    @Test
    fun resumeWithExistingWindowFocusRestoresImmersiveMode() {
        UserSettings(activity).immersiveMode = ImmersiveModeOption.ALWAYS_ON
        activityController.windowFocusChanged(true)
        activityController.pause()
        val hiddenBars =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val decorView = activity.window.decorView
        decorView.systemUiVisibility =
            decorView.systemUiVisibility and hiddenBars.inv()

        // No new window-focus callback: resume itself must repair the policy.
        activityController.resume()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(hiddenBars, decorView.systemUiVisibility and hiddenBars)
    }

    @Suppress("DEPRECATION")
    @Test
    fun lateWindowFocusCallbackCannotHideBarsWhilePaused() {
        UserSettings(activity).immersiveMode = ImmersiveModeOption.ALWAYS_ON
        activityController.pause()
        val hiddenBars =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val decorView = activity.window.decorView
        decorView.systemUiVisibility =
            decorView.systemUiVisibility and hiddenBars.inv()

        activityController.windowFocusChanged(true)
        assertEquals(0, decorView.systemUiVisibility and hiddenBars)
        activityController.resume()
    }

    @Suppress("DEPRECATION")
    @Test
    fun focusRecoveryRespectsOnlyWhenLockedAndTheCurrentLockTaskState() {
        UserSettings(activity).immersiveMode = ImmersiveModeOption.ONLY_WHEN_LOCKED
        val manager =
            activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val hiddenBars =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val decorView = activity.window.decorView
        decorView.systemUiVisibility =
            decorView.systemUiVisibility and hiddenBars.inv()
        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE)
        activityController.windowFocusChanged(true)
        assertEquals(0, decorView.systemUiVisibility and hiddenBars)

        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED)
        activityController.windowFocusChanged(false)
        activityController.windowFocusChanged(true)
        assertEquals(hiddenBars, decorView.systemUiVisibility and hiddenBars)
        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE)
    }

    @Test
    fun oversizedWebViewUrlDoesNotCreateAnUnboundedEditableTextField() {
        val webView = webView()
        val longUrl = "https://example.invalid/" + "x".repeat(30_000)
        webView.loadUrl(longUrl)
        webView.webViewClient.doUpdateVisitedHistory(webView, longUrl, false)
        frames()

        val nodes = mutableListOf<androidx.compose.ui.semantics.SemanticsNode>()
        fun collect(view: View) {
            if (view is ViewRootForTest) {
                nodes +=
                    view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
                nodes +=
                    view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
            }
            if (view is ViewGroup) {
                (0 until view.childCount).forEach { collect(view.getChildAt(it)) }
            }
        }
        collect(view)

        assertTrue(
            "Long URLs should have a copy action instead of requiring editable text layout",
            nodes.any {
                it.config
                    .getOrNull(SemanticsProperties.ContentDescription)
                    ?.contains("Copy full URL") == true
            },
        )
        assertTrue(
            "The full URL must not be sent to a Compose text field",
            nodes.none {
                it.config.getOrNull(SemanticsProperties.EditableText)?.text ==
                    longUrl
            },
        )
        assertTrue(
            "A bounded URL preview should still be visible",
            nodes.any {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text ->
                    text.text.startsWith("https://example.invalid/") &&
                        text.text.endsWith("\u2026") &&
                        text.text.length <= 257
                } == true
            },
        )

        // A long URL remains copyable in full even though it is not editable.
        val copyNode = nodes.first {
            it.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.contains("Copy full URL") == true &&
                it.config.getOrNull(SemanticsActions.OnClick) != null
        }
        assertTrue(
            copyNode.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() ==
                true
        )
        frames()
        val clipboard =
            activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(longUrl, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Tapping the preview offers an empty replacement field, not a huge
        // CoreTextField that would immediately repeat the expensive measurement.
        val preview = nodes.first {
            it.config.getOrNull(SemanticsActions.OnClick)?.label ==
                "Enter a replacement URL"
        }
        assertTrue(
            preview.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() ==
                true
        )
        frames()
        nodes.clear()
        collect(view)
        assertTrue(
            nodes.any {
                it.config.getOrNull(SemanticsProperties.EditableText)?.text == ""
            }
        )
        assertTrue(
            nodes.none {
                it.config.getOrNull(SemanticsProperties.EditableText)?.text ==
                    longUrl
            }
        )
    }

    @Test
    fun iframeProgressCannotShowTheBarBeforeOrAfterAMainFrameLoad() {
        val webView = webView()
        val client = webView.webViewClient
        val chrome = requireNotNull(webView.webChromeClient)
        chrome.onProgressChanged(webView, 10)
        frames()
        assertTrue(loadingProgress().isEmpty())

        val url = "https://example.invalid/signage"
        webView.loadUrl(url)
        client.onPageStarted(webView, url, null)
        chrome.onProgressChanged(webView, 40)
        frames()
        assertEquals(listOf(0.4f), loadingProgress())

        chrome.onProgressChanged(webView, 100)
        frames()
        assertTrue(loadingProgress().isEmpty())
        // The page-finished callback may be delayed while iframe loads continue.
        for (progress in listOf(5, 60, 20)) {
            chrome.onProgressChanged(webView, progress)
            frames()
            assertTrue(loadingProgress().isEmpty())
        }
        client.onPageFinished(webView, url)
        chrome.onProgressChanged(webView, 15)
        frames()
        assertTrue(loadingProgress().isEmpty())
    }

    @Test
    fun mainFrameFinishHidesProgressAndReloadingTheSameUrlShowsItAgain() {
        val webView = webView()
        val client = webView.webViewClient
        val chrome = requireNotNull(webView.webChromeClient)
        val url = "https://example.invalid/signage"
        webView.loadUrl(url)
        client.onPageStarted(webView, url, null)
        chrome.onProgressChanged(webView, 45)
        frames()
        assertEquals(listOf(0.45f), loadingProgress())

        // Some providers finish the main frame before reporting 100% progress.
        client.onPageFinished(webView, url)
        chrome.onProgressChanged(webView, 15)
        frames()
        assertTrue(loadingProgress().isEmpty())

        webView.reload()
        client.onPageStarted(webView, url, null)
        chrome.onProgressChanged(webView, 35)
        frames()
        assertEquals(listOf(0.35f), loadingProgress())
    }

    @Test
    fun anOlderPageFinishCannotHideANewerMainFrameLoad() {
        val webView = webView()
        val client = webView.webViewClient
        val chrome = requireNotNull(webView.webChromeClient)
        val first = "https://example.invalid/first"
        val second = "https://example.invalid/second"
        webView.loadUrl(first)
        client.onPageStarted(webView, first, null)
        webView.loadUrl(second)
        client.onPageStarted(webView, second, null)
        chrome.onProgressChanged(webView, 60)
        client.onPageFinished(webView, first)
        frames()
        assertEquals(listOf(0.6f), loadingProgress())

        client.onPageFinished(webView, second)
        frames()
        assertTrue(loadingProgress().isEmpty())
    }

    @Test
    fun subframeErrorsKeepProgressWhileMainFrameErrorsHideIt() {
        val webView = webView()
        val client = webView.webViewClient as WebViewClientCompat
        val chrome = requireNotNull(webView.webChromeClient)
        val url = "https://example.invalid/signage"
        val error =
            object : WebResourceErrorCompat() {
                override fun getErrorCode(): Int = -2

                override fun getDescription(): CharSequence = "Unable to connect"
            }
        webView.loadUrl(url)
        client.onPageStarted(webView, url, null)
        chrome.onProgressChanged(webView, 45)
        client.onReceivedError(
            webView,
            documentRequest("https://example.invalid/frame", mainFrame = false),
            error,
        )
        frames()
        assertEquals(listOf(0.45f), loadingProgress())

        client.onReceivedError(webView, documentRequest(url), error)
        chrome.onProgressChanged(webView, 20)
        frames()
        assertTrue(loadingProgress().isEmpty())
    }

    @Test
    fun mainFrameServerErrorsRetryAndANewSuccessfulLoadStopsRetries() {
        val settings = UserSettings(activity)
        settings.refreshOnLoadingErrorIntervalSeconds = 5
        val webView = webView()
        val client = webView.webViewClient
        val url = "https://example.invalid/signage"
        for (status in listOf(500, 502, 503, 599)) {
            loadWithMarker(webView, url)
            requireNotNull(webView.webChromeClient).onProgressChanged(webView, 45)
            client.onReceivedHttpError(
                webView,
                documentRequest(url),
                httpError(status),
            )
            client.onPageFinished(webView, url)
            frames()
            assertTrue(loadingProgress().isEmpty())
            assertLoadMarker(webView, expected = true)

            shadowOf(Looper.getMainLooper()).idleFor(6, TimeUnit.SECONDS)
            frames()
            assertEquals(url, shadowOf(webView).lastLoadedUrl)
            assertLoadMarker(webView, expected = false)

            loadWithMarker(webView, url)
            client.onPageFinished(webView, url)
            frames()
            shadowOf(Looper.getMainLooper()).idleFor(6, TimeUnit.SECONDS)
            frames()
            assertLoadMarker(webView, expected = true)
        }
    }

    @Test
    fun clientErrorsSubresourcesAndSupersededServerErrorsDoNotRetry() {
        UserSettings(activity).refreshOnLoadingErrorIntervalSeconds = 5
        val webView = webView()
        val client = webView.webViewClient
        val url = "https://example.invalid/signage"
        for (status in listOf(400, 401, 403, 404, 429, 499)) {
            loadWithMarker(webView, url)
            client.onReceivedHttpError(
                webView,
                documentRequest(url),
                httpError(status),
            )
            frames()
            shadowOf(Looper.getMainLooper()).idleFor(6, TimeUnit.SECONDS)
            frames()
            assertLoadMarker(webView, expected = true)
        }

        for (request in
            listOf(
                documentRequest(url, mainFrame = false),
                documentRequest("https://example.invalid/older"),
            )) {
            loadWithMarker(webView, url)
            requireNotNull(webView.webChromeClient).onProgressChanged(webView, 45)
            client.onReceivedHttpError(webView, request, httpError(503))
            frames()
            assertEquals(listOf(0.45f), loadingProgress())
            shadowOf(Looper.getMainLooper()).idleFor(6, TimeUnit.SECONDS)
            frames()
            assertLoadMarker(webView, expected = true)
        }
    }

    @Test
    fun loadingErrorRetriesAreIndependentOfAllowRefreshAndRespectInterval() {
        val settings = UserSettings(activity)
        val webView = webView()
        val client = webView.webViewClient as WebViewClientCompat
        val url = "https://example.invalid/signage"
        val connectionError =
            object : WebResourceErrorCompat() {
                override fun getErrorCode(): Int = -2

                override fun getDescription(): CharSequence = "Unable to connect"
            }
        for (serverError in listOf(false, true)) {
            for ((interval, allowed, retries) in
                listOf(
                    Triple(0, false, false),
                    Triple(0, true, false),
                    Triple(5, false, true),
                    Triple(5, true, true),
                )) {
                settings.refreshOnLoadingErrorIntervalSeconds = interval
                settings.allowRefresh = allowed
                loadWithMarker(webView, url)
                if (serverError) {
                    client.onReceivedHttpError(
                        webView,
                        documentRequest(url),
                        httpError(503),
                    )
                } else {
                    client.onReceivedError(
                        webView,
                        documentRequest(url),
                        connectionError,
                    )
                }
                frames()
                assertLoadMarker(webView, expected = true)
                shadowOf(Looper.getMainLooper()).idleFor(6, TimeUnit.SECONDS)
                frames()
                assertLoadMarker(webView, expected = !retries)
            }
        }

        settings.allowRefresh = false
        settings.refreshOnLoadingErrorIntervalSeconds = 5
        loadWithMarker(webView, url)
        webView.webViewClient.onReceivedHttpError(
            webView,
            documentRequest(url),
            httpError(503),
        )
        frames()
        settings.refreshOnLoadingErrorIntervalSeconds = 0
        shadowOf(Looper.getMainLooper()).idleFor(6, TimeUnit.SECONDS)
        frames()
        assertLoadMarker(webView, expected = true)
    }

    @Test
    fun navigationAndLeavingTheWebViewCancelServerErrorRetries() {
        UserSettings(activity).refreshOnLoadingErrorIntervalSeconds = 5
        val webView = webView()
        val failed = "https://example.invalid/failed"
        loadWithMarker(webView, failed)
        webView.webViewClient.onReceivedHttpError(
            webView,
            documentRequest(failed),
            httpError(503),
        )
        frames()
        loadWithMarker(webView, "https://example.invalid/recovered")
        frames()
        shadowOf(Looper.getMainLooper()).idleFor(6, TimeUnit.SECONDS)
        frames()
        assertLoadMarker(webView, expected = true)

        webView.webViewClient.onReceivedHttpError(
            webView,
            documentRequest("https://example.invalid/recovered"),
            httpError(503),
        )
        frames()
        controller().navigate(Screen.SettingsWebLifecycle.route)
        frames()
        shadowOf(Looper.getMainLooper()).idleFor(6, TimeUnit.SECONDS)
        frames()
        assertEquals(
            Screen.SettingsWebLifecycle.route,
            controller().currentDestination?.route,
        )
        assertLoadMarker(webView, expected = true)
    }

    @Test
    fun screenWakeRefreshIsOptInAndIndependentOfAllowRefresh() {
        val settings = UserSettings(activity)
        val url = "https://example.invalid/signage"
        for ((enabled, allowed, refreshes) in
            listOf(
                Triple(false, false, false),
                Triple(false, true, false),
                Triple(true, false, true),
                Triple(true, true, true),
            )) {
            settings.refreshOnScreenWake = enabled
            settings.allowRefresh = allowed
            // Apply settings through the same WebView replacement used by the UI.
            navigateToWebViewScreen(controller())
            frames()
            activityController.windowFocusChanged(true)
            val webView = webView()
            loadWithMarker(webView, url)
            screenBroadcast(Intent.ACTION_SCREEN_OFF)
            screenBroadcast(Intent.ACTION_SCREEN_ON)
            frames()
            assertLoadMarker(webView, expected = !refreshes)
            assertEquals(url, shadowOf(webView).lastLoadedUrl)
        }
    }

    @Test
    fun screenWakeCannotRefreshSettingsOrAWebViewReplacedBeforeWake() {
        UserSettings(activity).refreshOnScreenWake = true
        navigateToWebViewScreen(controller())
        frames()
        val before = webView()
        loadWithMarker(before, "https://example.invalid/signage")
        screenBroadcast(Intent.ACTION_SCREEN_OFF)
        controller().navigate(Screen.SettingsWebLifecycle.route)
        frames()
        screenBroadcast(Intent.ACTION_SCREEN_ON)
        frames()
        assertEquals(
            Screen.SettingsWebLifecycle.route,
            controller().currentDestination?.route,
        )
        assertLoadMarker(before, expected = true)

        navigateToWebViewScreen(controller())
        frames()
        val after = webView()
        activityController.windowFocusChanged(true)
        loadWithMarker(after, "https://example.invalid/new-session")
        // A new owner cannot inherit the retired owner's sleep transition.
        screenBroadcast(Intent.ACTION_SCREEN_ON)
        frames()
        assertLoadMarker(after, expected = true)
    }

    private fun httpError(status: Int) =
        WebResourceResponse("text/html", "UTF-8", status, "Error", emptyMap(), null)

    private fun loadWithMarker(webView: WebView, url: String) {
        webView.loadUrl(url, mapOf("X-Initial-Load" to "true"))
        webView.webViewClient.onPageStarted(webView, url, null)
        webView.webViewClient.doUpdateVisitedHistory(webView, url, false)
        frames()
    }

    private fun assertLoadMarker(webView: WebView, expected: Boolean) {
        assertEquals(
            expected,
            shadowOf(webView)
                .lastAdditionalHttpHeaders
                ?.containsKey("X-Initial-Load") == true,
        )
    }

    @Suppress(
        "DEPRECATION"
    ) // Control power state and broadcast delivery separately.
    private fun screenBroadcast(action: String) {
        val power = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(power).setIsInteractive(action == Intent.ACTION_SCREEN_ON)
        activity.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun stoppingALoadPreventsLaterProgressFromShowingTheBar() {
        val webView = webView()
        val chrome = requireNotNull(webView.webChromeClient)
        val url = "https://example.invalid/signage"
        webView.loadUrl(url)
        webView.webViewClient.onPageStarted(webView, url, null)
        chrome.onProgressChanged(webView, 40)
        frames()
        assertEquals(listOf(0.4f), loadingProgress())

        webView.stopLoading()
        chrome.onProgressChanged(webView, 20)
        frames()
        assertTrue(loadingProgress().isEmpty())
    }

    @Test
    fun theVisibilitySettingStillHidesProgressDuringMainFrameLoads() {
        val settings = UserSettings(activity)
        settings.showLoadingProgressBar = false
        val webView = webView()
        val chrome = requireNotNull(webView.webChromeClient)
        val url = "https://example.invalid/signage"
        webView.loadUrl(url)
        webView.webViewClient.onPageStarted(webView, url, null)
        chrome.onProgressChanged(webView, 40)
        frames()
        assertTrue(loadingProgress().isEmpty())

        settings.showLoadingProgressBar = true
        navigateToWebViewScreen(controller())
        frames()
        val enabledWebView = webView()
        assertNotSame(webView, enabledWebView)
        enabledWebView.loadUrl(url)
        enabledWebView.webViewClient.onPageStarted(enabledWebView, url, null)
        requireNotNull(enabledWebView.webChromeClient)
            .onProgressChanged(enabledWebView, 50)
        frames()
        assertEquals(listOf(0.5f), loadingProgress())
    }

    @Test
    fun directLoadsAndHeaderLoadsApplyDocumentRulesBeforeLoading() {
        documentRules($$"||blocked.example^$doc\n@@||blocked.example/safe$document")
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
        documentRules($$"||forms.example^$document,method=POST")
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
        documentRules($$"||blocked.example^$document\n@@||blocked.example/safe$doc")
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
        documentRules($$"||forms.example^$document,method=POST")
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
        documentRules($$"||blocked.example^$document")
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
        documentRules($$"||blocked.example/report.pdf$doc")
        val source = "https://blocked.example/report.pdf"
        val wrapper =
            "${Constants.PDF_JS_ASSETS_DUMMY_URL}?wk_pdf_url=${Uri.encode(source)}"
        activityController.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse(wrapper)))
        frames()
        assertBlockedDocument(webView(), source)
        assertNull(webView().getTag(uk.nktnet.webviewkiosk.R.id.pdf_source_token))
    }

    @Test
    fun parameterRemovalCleansAppLoadsAndRetainsHeadersExceptionsAndDisable() {
        documentRules(
            $$"*$removeparam=track\n@@||site.example/safe$removeparam=track"
        )
        val webView = webView()
        val headers = mapOf("X-Test" to "kept")
        webView.loadUrl("https://site.example/?track=1&keep=2#x", headers)
        assertEquals(
            "https://site.example/?keep=2#x",
            shadowOf(webView).lastLoadedUrl,
        )
        assertEquals(headers, shadowOf(webView).lastAdditionalHttpHeaders)
        webView.loadUrl("https://site.example/safe?track=1")
        assertEquals(
            "https://site.example/safe?track=1",
            shadowOf(webView).lastLoadedUrl,
        )
        ContentBlockingService.get(activity).setEnabled(false)
        webView.loadUrl("https://site.example/?track=1")
        assertEquals(
            "https://site.example/?track=1",
            shadowOf(webView).lastLoadedUrl,
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun parameterRemovalReplaysOnlyKnownMainFrameGetCallbacks() {
        documentRules($$"*$removeparam=track")
        val webView = webView()
        val client = webView.webViewClient
        val url = "https://site.example/?track=1&keep=2"
        val headers = mapOf("X-Navigation" to "kept")
        assertTrue(
            client.shouldOverrideUrlLoading(
                webView,
                documentRequest(url, redirect = true, headers = headers),
            )
        )
        assertEquals("https://site.example/?keep=2", shadowOf(webView).lastLoadedUrl)
        assertEquals(headers, shadowOf(webView).lastAdditionalHttpHeaders)
        assertFalse(
            client.shouldOverrideUrlLoading(
                webView,
                documentRequest(url, mainFrame = false),
            )
        )
        assertFalse(
            client.shouldOverrideUrlLoading(
                webView,
                documentRequest(url, method = "POST"),
            )
        )
        assertFalse(client.shouldOverrideUrlLoading(webView, url))
        assertNull(
            client.shouldInterceptRequest(
                webView,
                documentRequest(url, method = "POST"),
            )
        )
        assertEquals("https://site.example/?keep=2", shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun parameterRemovalCannotBypassOriginalOrCleanedDocumentBlocks() {
        documentRules(
            $$"*$removeparam=track\n" +
                $$"||blocked.example/?track=$doc\n" +
                $$"|https://cleaned.example/|$doc"
        )
        val webView = webView()
        webView.loadUrl("https://blocked.example/?track=1")
        assertBlockedDocument(webView, "https://blocked.example/?track=1")
        webView.loadUrl("https://cleaned.example/?track=1")
        assertBlockedDocument(webView, "https://cleaned.example/")
        assertTrue(
            webView.webViewClient.shouldOverrideUrlLoading(
                webView,
                documentRequest("https://blocked.example/?track=2"),
            )
        )
        assertBlockedDocument(webView, "https://blocked.example/?track=2")
    }

    @Test
    fun externalLoadsCleanParametersBeforePdfRoutingAndCheckOriginalUrlPolicy() {
        documentRules($$"*$removeparam=track")
        // Only routing is exercised here: JavaScript stays disabled. The real
        // source fetch and redirect URLs are covered by the HTTP server tests.
        val assets = java.io.File(activity.filesDir, Constants.PDF_JS_ASSETS_DIR)
        assets.mkdirs()
        val version =
            PdfJsManager::class
                .java
                .getDeclaredField("PDF_JS_VERSION")
                .apply { isAccessible = true }
                .get(null) as String
        java.io.File(assets, ".version").writeText(version)
        listOf("pdf.mjs", "pdf.worker.mjs").forEach {
            java.io.File(assets, it).writeText("// Routing test fixture")
        }
        UserSettings(activity).supportPdfRendering = true
        assertTrue(PdfJsManager.areAssetsReady(activity))
        frames()
        val url = "https://site.example/report.pdf?track=1&id=2"
        activityController.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        frames()
        assertEquals(
            "https://site.example/report.pdf?id=2",
            Uri.parse(shadowOf(webView()).lastLoadDataWithBaseURL.baseUrl)
                .getQueryParameter("wk_pdf_url"),
        )
        UserSettings(activity).websiteBlacklist = "track=blocked"
        frames()
        val blocked = "https://site.example/?track=blocked"
        activityController.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse(blocked)))
        frames()
        assertEquals(blocked, shadowOf(webView()).lastLoadDataWithBaseURL.baseUrl)
        assertTrue(
            shadowOf(webView())
                .lastLoadDataWithBaseURL
                .data
                .contains("Access Blocked")
        )
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
        withTimeout(10.seconds) { service.awaitReload(service.reload()) }
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
        headers: Map<String, String> = emptyMap(),
    ) =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame() = mainFrame

            override fun isRedirect() = redirect

            override fun hasGesture() = true

            override fun getMethod() = method

            override fun getRequestHeaders() = headers
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
        after.webViewClient.onPageStarted(after, currentUrl, null)
        requireNotNull(after.webChromeClient).onProgressChanged(after, 40)
        oldClient.onPageStarted(before, "https://example.invalid/retired", null)
        oldClient.doUpdateVisitedHistory(
            before,
            "https://example.invalid/retired",
            false,
        )
        oldClient.onPageFinished(before, "https://example.invalid/retired")
        oldClient.onReceivedHttpError(
            before,
            documentRequest("https://example.invalid/retired"),
            httpError(503),
        )
        oldChrome?.onProgressChanged(before, 5)
        frames()

        assertEquals(currentHistory, settings.historyStack)
        assertEquals(0, settings.historyIndex)
        assertEquals(currentUrl, shadowOf(after).lastLoadedUrl)
        assertEquals(listOf(0.4f), loadingProgress())
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

    private fun loadingProgress(): List<Float> {
        val progress = mutableListOf<Float>()
        fun collect(view: View) {
            if (view is ViewRootForTest) {
                view.semanticsOwner
                    .getAllSemanticsNodes(mergingEnabled = true)
                    .mapNotNullTo(progress) {
                        it.config
                            .getOrNull(SemanticsProperties.ProgressBarRangeInfo)
                            ?.current
                    }
            }
            if (view is ViewGroup) {
                (0 until view.childCount).forEach { collect(view.getChildAt(it)) }
            }
        }
        collect(view)
        return progress
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
