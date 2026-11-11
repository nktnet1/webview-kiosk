package uk.nktnet.webviewkiosk.ui.screens

import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
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
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w480dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsMoreActionsScreenTest {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var view: ComposeView
    private lateinit var navController: NavHostController
    private lateinit var recomposer: Recomposer
    private val roots = mutableListOf<ViewRootForTest>()
    private val clock = BroadcastFrameClock()
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
    private var frameTime = 0L

    @Before
    fun setUp() {
        ViewRootForTest.onViewCreatedCallback = { roots += it }
        activityController =
            Robolectric.buildActivity(AppCompatActivity::class.java)
                .setup()
                .visible()
        val activity = activityController.get()
        navController =
            NavHostController(activity).apply {
                setLifecycleOwner(activity)
                setViewModelStore(ViewModelStore())
                navigatorProvider.addNavigator(ComposeNavigator())
                graph =
                    createGraph(
                        startDestination = Screen.SettingsMoreActions.route
                    ) {
                        listOf(
                                Screen.SettingsMoreActions,
                                Screen.SettingsWebContentFiles,
                                Screen.SettingsContentBlocking,
                                Screen.SettingsWebBrowsingSitePermissions,
                                Screen.SettingsDeviceOwner,
                            )
                            .forEach { screen -> composable(screen.route) {} }
                    }
            }
        recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view = ComposeView(activity)
        view.setParentCompositionContext(recomposer)
        activity.setContentView(view)
        view.setContent {
            MaterialTheme { SettingsMoreActionsScreen(navController) }
        }
        frames()
    }

    @After
    fun tearDown() {
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        activityController.pause().stop().destroy()
        ViewRootForTest.onViewCreatedCallback = null
    }

    @Test
    fun manageActionsAppearInTheRequestedTwoByTwoOrder() {
        val localFiles =
            textNode(message(R.string.settings_more_action_local_files))
                .boundsInWindow
        val contentBlocking =
            textNode(message(R.string.settings_more_action_content_blocking))
                .boundsInWindow
        val sitePermissions =
            textNode(message(R.string.settings_more_action_site_permissions))
                .boundsInWindow
        val deviceOwner =
            textNode(message(R.string.settings_more_action_device_owner))
                .boundsInWindow

        assertEquals(localFiles.top, contentBlocking.top, 1f)
        assertTrue(localFiles.left < contentBlocking.left)
        assertEquals(sitePermissions.top, deviceOwner.top, 1f)
        assertTrue(sitePermissions.left < deviceOwner.left)
        assertTrue(localFiles.top < sitePermissions.top)
        assertTrue(contentBlocking.top < deviceOwner.top)
    }

    @Test
    fun manageActionsNavigateToTheirMatchingSettingsScreens() {
        listOf(
                message(R.string.settings_more_action_local_files) to
                    Screen.SettingsWebContentFiles,
                message(R.string.settings_more_action_content_blocking) to
                    Screen.SettingsContentBlocking,
                message(R.string.settings_more_action_site_permissions) to
                    Screen.SettingsWebBrowsingSitePermissions,
                message(R.string.settings_more_action_device_owner) to
                    Screen.SettingsDeviceOwner,
            )
            .forEach { (label, screen) ->
                val button =
                    generateSequence(textNode(label)) { it.parent }
                        .first {
                            it.config.getOrNull(SemanticsActions.OnClick) != null
                        }
                assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke())
                frames()
                assertEquals(screen.route, navController.currentDestination?.route)
                assertTrue(navController.popBackStack())
                frames()
            }
    }

    private fun message(id: Int) = activityController.get().getString(id)

    private fun textNode(text: String): SemanticsNode =
        roots
            .filter { it.view.isAttachedToWindow }
            .flatMap {
                it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
            }
            .first { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any {
                    it.text == text
                } == true
            }

    private fun frames() {
        repeat(8) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            frameTime += 16_000_000L
            clock.sendFrame(frameTime)
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 480, 1000)
            roots
                .filter { it.view.isAttachedToWindow }
                .forEach { it.measureAndLayoutForTest() }
        }
    }
}
