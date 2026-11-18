package uk.nktnet.webviewkiosk.ui.screens

import android.os.Build
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
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
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttDebugLogCard
import uk.nktnet.webviewkiosk.ui.components.setting.unifiedpush.UnifiedPushDebugLogCard

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R], qualifiers = "w480dp-h1800dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsNavigationCardsTest {
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
                        startDestination = Screen.SettingsWebBrowsing.route
                    ) {
                        listOf(
                                Screen.SettingsWebBrowsing,
                                Screen.SettingsWebBrowsingSitePermissions,
                                Screen.SettingsDeviceOwner,
                                Screen.SettingsMqttDebug,
                                Screen.SettingsUnifiedPushDebug,
                            )
                            .forEach { screen -> composable(screen.route) {} }
                    }
            }
        recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view = ComposeView(activity)
        view.setParentCompositionContext(recomposer)
        activity.setContentView(view)
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
    fun webBrowsingSitePermissionsUsesNavigationCard() {
        render { SettingsWebBrowsingScreen(navController) }
        assertNavigates(
            R.string.settings_site_permissions_title,
            R.string.settings_site_permissions_description,
            Screen.SettingsWebBrowsingSitePermissions,
        )
    }

    @Test
    fun devicePoliciesUsesNavigationCard() {
        render { SettingsDeviceScreen(navController) }
        assertNavigates(
            R.string.settings_device_owner_title,
            R.string.settings_device_owner_description,
            Screen.SettingsDeviceOwner,
        )
    }

    @Test
    fun mqttDebugLogUsesNavigationCard() {
        render { MqttDebugLogCard(navController) }
        assertNavigates(
            R.string.settings_debug_log_title,
            R.string.settings_mqtt_debug_log_description,
            Screen.SettingsMqttDebug,
        )
    }

    @Test
    fun unifiedPushDebugLogUsesNavigationCard() {
        render { UnifiedPushDebugLogCard(navController) }
        assertNavigates(
            R.string.settings_debug_log_title,
            R.string.settings_unifiedpush_debug_log_description,
            Screen.SettingsUnifiedPushDebug,
        )
    }

    private fun render(content: @Composable () -> Unit) {
        view.setContent { MaterialTheme { content() } }
        frames()
    }

    private fun assertNavigates(
        titleRes: Int,
        descriptionRes: Int,
        destination: Screen,
    ) {
        val title = textNode(activityController.get().getString(titleRes))
        val description =
            textNode(activityController.get().getString(descriptionRes))
        val card =
            generateSequence(title) { it.parent }
                .first {
                    it.config.getOrNull(SemanticsActions.OnClick) != null
                }
        assertTrue(
            generateSequence(description) { it.parent }.any { it.id == card.id }
        )
        assertTrue(card.config[SemanticsActions.OnClick].action!!.invoke())
        frames()
        assertEquals(destination.route, navController.currentDestination?.route)
    }

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
                View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 480, 1800)
            roots
                .filter { it.view.isAttachedToWindow }
                .forEach { it.measureAndLayoutForTest() }
        }
    }
}
