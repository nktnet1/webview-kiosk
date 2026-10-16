package uk.nktnet.webviewkiosk.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.NavHostController
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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
import uk.nktnet.webviewkiosk.config.HistoryEntry
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
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
