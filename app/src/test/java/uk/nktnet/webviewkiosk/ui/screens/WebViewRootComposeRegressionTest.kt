package uk.nktnet.webviewkiosk.ui.screens

import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.utils.WEBVIEW_RECREATION_REQUEST_KEY
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen

/** Drive the production SetupNavHost graph and keyed root content with a deterministic probe. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class WebViewRootComposeRegressionTest {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private lateinit var view: ComposeView
    private lateinit var navController: NavHostController
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
    private lateinit var recomposer: Recomposer
    private var frameTime = 0L
    private val showHost = mutableStateOf(true)
    private val extraRecomposition = mutableStateOf(0)
    private val mounted = mutableListOf<Int>()
    private val disposed = mutableListOf<Int>()
    private var lastId = 0

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup().visible()
        activity = activityController.get()
        navController = newController()
        recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view = ComposeView(activity)
        view.setParentCompositionContext(recomposer)
        activity.setContentView(view)
        mount()
    }

    @After
    fun tearDown() {
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        activityController.pause().stop().destroy()
    }

    @Test
    fun repeatedRefreshesDisposeOldCompositionExactlyOnceWithoutReplacingRoot() {
        val root = navController.getBackStackEntry(Screen.WebView.route)
        assertEquals(1, mounted.size)
        repeat(4) { index ->
            navigateToWebViewScreen(navController)
            frames()
            assertSame(root, navController.currentBackStackEntry)
            assertEquals(index + 2, mounted.size)
            assertEquals(index + 1, disposed.size)
            assertEquals(mounted.take(index + 1), disposed)
        }
        extraRecomposition.value++
        frames()
        assertEquals(5, mounted.size)
        assertEquals(4, disposed.size)
        assertEquals(1, navController.currentBackStack.value.count { it.destination.route == Screen.WebView.route })
    }

    @Test
    fun temporaryFileImportLikeRemovalRemountsWithoutReplacingGraphOrBackStack() {
        val graph = navController.graph
        val root = navController.getBackStackEntry(Screen.WebView.route)
        showHost.value = false
        frames()
        assertEquals(1, disposed.size)
        assertSame(graph, navController.graph)

        showHost.value = true
        frames()
        assertSame(graph, navController.graph)
        assertSame(root, navController.getBackStackEntry(Screen.WebView.route))
        assertEquals(2, mounted.size)
        assertEquals(1, disposed.size)

        navigateToWebViewScreen(navController)
        frames()
        assertSame(root, navController.currentBackStackEntry)
        assertEquals(3, mounted.size)
        assertEquals(2, disposed.size)
    }

    @Test
    fun rapidNavigationAndRootRefreshesNeverDuplicateTheRoot() {
        val root = navController.getBackStackEntry(Screen.WebView.route)
        repeat(12) {
            navController.navigate(Screen.Settings.route)
            navigateToWebViewScreen(navController)
            navigateToWebViewScreen(navController)
            frames()
            assertSame(root, navController.currentBackStackEntry)
            assertEquals(1, navController.currentBackStack.value.count { entry -> entry.destination.route == Screen.WebView.route })
            assertFalse(navController.currentBackStack.value.any { entry -> entry.destination.route == Screen.Settings.route })
        }
        assertEquals(24, root.savedStateHandle.get<Int>(WEBVIEW_RECREATION_REQUEST_KEY))
    }

    @Test
    fun returningDuringAComposedAdminTransitionCannotInvalidateTheRoot() {
        val root = navController.getBackStackEntry(Screen.WebView.route)
        repeat(5) {
            navController.navigate(Screen.AdminRestrictionsChanged.route)
            frames() // Allow the outgoing and incoming destinations to overlap in NavHost.
            navigateToWebViewScreen(navController)
            frames()
            assertSame(root, navController.currentBackStackEntry)
            assertEquals(1, navController.currentBackStack.value.count { entry ->
                entry.destination.route == Screen.WebView.route
            })
            assertFalse(navController.currentBackStack.value.any { entry ->
                entry.destination.route == Screen.AdminRestrictionsChanged.route
            })
        }
        assertEquals(5, root.savedStateHandle.get<Int>(WEBVIEW_RECREATION_REQUEST_KEY))
    }

    @Test
    fun controllerStateRestoresRefreshVersionAndSingleRootAfterRecreation() {
        navigateToWebViewScreen(navController)
        frames()
        val prior = navController.getBackStackEntry(Screen.WebView.route)
        val snapshot: Bundle = navController.saveState() ?: error("no navigation state")
        showHost.value = false
        frames()

        navController = newController().apply { restoreState(snapshot) }
        showHost.value = true
        mount()
        val root = navController.getBackStackEntry(Screen.WebView.route)
        assertTrue(root !== prior)
        assertEquals(1, root.savedStateHandle.get<Int>(WEBVIEW_RECREATION_REQUEST_KEY))
        assertEquals(1, navController.currentBackStack.value.count { it.destination.route == Screen.WebView.route })
        navigateToWebViewScreen(navController)
        frames()
        assertSame(root, navController.currentBackStackEntry)
        assertEquals(2, root.savedStateHandle.get<Int>(WEBVIEW_RECREATION_REQUEST_KEY))
    }

    private fun newController() = NavHostController(activity).apply {
        setLifecycleOwner(activity)
        setViewModelStore(ViewModelStore())
        navigatorProvider.addNavigator(ComposeNavigator())
        navigatorProvider.addNavigator(DialogNavigator())
    }

    private fun mount() {
        view.setContent {
            extraRecomposition.value
            if (showHost.value) {
                SetupNavHost(navController) {
                    val id = remember { ++lastId }
                    DisposableEffect(id) {
                        mounted += id
                        onDispose { disposed += id }
                    }
                }
            }
        }
        frames()
    }

    private fun frames() {
        repeat(8) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            frameTime += 16_000_000L
            clock.sendFrame(frameTime)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
