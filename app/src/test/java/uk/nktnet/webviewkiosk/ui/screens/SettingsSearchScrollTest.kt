package uk.nktnet.webviewkiosk.ui.screens

import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import java.time.Duration
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.ui.components.setting.LocalSettingsSearchTarget
import uk.nktnet.webviewkiosk.ui.components.setting.SETTINGS_SEARCH_FLASH_DELAY_MS
import uk.nktnet.webviewkiosk.ui.components.setting.SettingsSearchTarget

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h480dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsSearchScrollTest {
    private lateinit var controller: ActivityController<AppCompatActivity>
    private lateinit var view: ComposeView
    private lateinit var recomposer: Recomposer
    private val roots = mutableListOf<ViewRootForTest>()
    private val clock = BroadcastFrameClock()
    private var frameTime = 0L
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        // Android grants this declared signature permission to the app's UID.
        shadowOf(context)
            .grantPermissions(
                "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
            )
        ViewRootForTest.onViewCreatedCallback = { roots += it }
        controller =
            Robolectric.buildActivity(AppCompatActivity::class.java)
                .setup()
                .visible()
        val activity = controller.get()
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
        controller.pause().stop().destroy()
        ViewRootForTest.onViewCreatedCallback = null
    }

    @Test
    fun openingALateSettingBringsTheActualRowIntoTheViewport() {
        val activity = controller.get()
        val title =
            activity.getString(R.string.appearance_show_loading_progress_bar_title)
        view.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalSettingsSearchTarget provides
                        SettingsSearchTarget(
                            UserSettingsKeys.Appearance.SHOW_LOADING_PROGRESS_BAR
                        ) {}
                ) {
                    SettingsAppearanceScreen(NavController(activity))
                }
            }
        }
        repeat(30) { renderFrame(advanceTime = false) }

        val nodes =
            roots
                .filter { it.view.isAttachedToWindow }
                .flatMap {
                    it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
                }
        val label = nodes.first { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any {
                it.text == title
            } == true
        }
        val scroll =
            nodes
                .first {
                    it.config.getOrNull(
                        SemanticsProperties.VerticalScrollAxisRange
                    ) != null
                }
                .config[SemanticsProperties.VerticalScrollAxisRange]

        assertTrue("A late setting must require scrolling", scroll.maxValue() > 0f)
        assertTrue("The destination should scroll past the top", scroll.value() > 0f)
        assertTrue(
            "The target should be within the screen: ${label.boundsInRoot}",
            label.boundsInRoot.bottom <= 480f && label.boundsInRoot.top >= 0f,
        )
    }

    @Test
    fun flashesOnlyOnceAfterScrollingAndWaiting() {
        assertEquals(
            "Wait a full second before flashing",
            1_000L,
            SETTINGS_SEARCH_FLASH_DELAY_MS,
        )
        val activity = controller.get()
        var consumed = 0
        view.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalSettingsSearchTarget provides
                        SettingsSearchTarget(
                            UserSettingsKeys.Appearance.SHOW_LOADING_PROGRESS_BAR
                        ) {
                            consumed++
                        }
                ) {
                    SettingsAppearanceScreen(NavController(activity))
                }
            }
        }

        repeat(15) { renderFrame(advanceTime = true) }
        assertEquals("Do not flash before the scroll and delay", 0, consumed)

        repeat(130) { renderFrame(advanceTime = true) }
        assertEquals(
            "The highlight must finish and consume its request",
            1,
            consumed,
        )

        repeat(20) { renderFrame(advanceTime = true) }
        assertEquals("The same selection must not flash again", 1, consumed)
    }

    @Test
    fun actualScreenRowsAgreeWithTheRegistryForEverySetting() {
        val activity = controller.get()
        // Topic previews need the same static configuration as the app startup.
        // Avoid creating or connecting a broker client in this UI test.
        MqttManager.updateConfig(activity, rebuildClient = false)
        for (page in settingsPages.filter { it.settings.isNotEmpty() }) {
            view.setContent {
                MaterialTheme { page.content(NavController(activity)) }
            }
            repeat(5) { renderFrame(advanceTime = false) }
            val actual =
                roots
                    .asSequence()
                    .filter { it.view.isAttachedToWindow }
                    .flatMap {
                        it.semanticsOwner.getAllSemanticsNodes(
                            mergingEnabled = false
                        )
                    }
                    .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
                    .filter { it.startsWith("settings_target:") }
                    .map { it.removePrefix("settings_target:") }
                    .toSet()
            assertEquals(
                "Incorrect searchable rows on ${page.route}",
                page.settings.map { it.key }.toSet(),
                actual,
            )
            view.disposeComposition()
        }
    }

    @Test
    fun informationAndActionTargetsExistOnTheirActualScreens() {
        for (screen in listOf(Screen.SettingsAbout, Screen.SettingsMoreActions)) {
            val page = settingsPages.single { it.screen == screen }
            view.setContent {
                MaterialTheme { page.content(NavController(controller.get())) }
            }
            repeat(5) { renderFrame(advanceTime = false) }
            val actual =
                roots
                    .filter { it.view.isAttachedToWindow }
                    .flatMap {
                        it.semanticsOwner.getAllSemanticsNodes(
                            mergingEnabled = false
                        )
                    }
                    .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
                    .toSet()
            for ((titleRes, kind) in page.items) {
                val id = settingsItemTargetId(controller.get(), kind, titleRes)
                assertTrue(
                    "Missing target $id on ${page.route}",
                    "settings_target:$id" in actual,
                )
            }
            view.disposeComposition()
        }
    }

    @Test
    fun everySearchablePageExistsInTheActualNavigationGraph() {
        lateinit var navigation: NavHostController
        view.setContent {
            MaterialTheme {
                navigation = rememberNavController()
                SetupNavHost(navigation) { Text("Webview") }
            }
        }
        repeat(5) { renderFrame(advanceTime = false) }
        val settingsGraph =
            navigation.graph.findNode("settings_list")
                as androidx.navigation.NavGraph
        for (page in settingsPages) {
            assertEquals(page.route, settingsGraph.findNode(page.route)?.route)
        }
    }

    @Test
    fun duplicateTranslatedLabelsStillTargetOnlyTheSelectedKey() {
        var consumed = 0
        view.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalSettingsSearchTarget provides
                        SettingsSearchTarget("selected") { consumed++ }
                ) {
                    androidx.compose.foundation.layout.Column {
                        uk.nktnet.webviewkiosk.ui.components.setting.fields
                            .GenericSettingFieldItem(
                                settingKey = "other",
                                label = "Same label",
                                value = "",
                                restricted = false,
                                onClick = {},
                            ) {
                                Text(it)
                            }
                        uk.nktnet.webviewkiosk.ui.components.setting.fields
                            .GenericSettingFieldItem(
                                settingKey = "selected",
                                label = "Same label",
                                value = "",
                                restricted = false,
                                onClick = {},
                            ) {
                                Text(it)
                            }
                    }
                }
            }
        }
        repeat(150) { renderFrame(advanceTime = true) }
        assertEquals(
            "Only the selected stable identity consumes the request",
            1,
            consumed,
        )
    }

    @Test
    fun importAndExportResultsOpenTheExistingDialogAndConsumeTheRequest() {
        for (title in listOf(R.string.ui_import, R.string.ui_export)) {
            val target = mutableStateOf<SettingsSearchTarget?>(null)
            var consumed = 0
            val id =
                settingsItemTargetId(
                    controller.get(),
                    SettingsSearchResultKind.ACTION,
                    title,
                )
            val navigation = NavController(controller.get())
            view.setContent {
                MaterialTheme {
                    CompositionLocalProvider(
                        LocalSettingsSearchTarget provides target.value
                    ) {
                        SettingsListScreen(navigation)
                    }
                }
            }
            // Search navigation reuses an already measured activity window.
            repeat(4) { renderFrame(advanceTime = false) }
            val previousDialog = ShadowDialog.getLatestDialog()
            target.value =
                SettingsSearchTarget(id) {
                    consumed++
                    target.value = null
                }
            // Verify dialog creation and one-shot consumption before platform
            // traversal. Rendering the existing tabbed dialogs is independent
            // of search and stalls in Robolectric's intrinsic text measurement.
            repeat(3) {
                Snapshot.sendApplyNotifications()
                frameTime += 16_000_000L
                clock.sendFrame(frameTime)
            }
            assertEquals(1, consumed)
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(
                "Search must open a fresh tool dialog",
                dialog != null && dialog !== previousDialog && dialog.isShowing,
            )
            view.disposeComposition()
        }
    }

    private fun renderFrame(advanceTime: Boolean) {
        Snapshot.sendApplyNotifications()
        if (advanceTime) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        } else {
            shadowOf(Looper.getMainLooper()).idle()
        }
        frameTime += 16_000_000L
        clock.sendFrame(frameTime)
        shadowOf(Looper.getMainLooper()).idle()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 400, 480)
        roots
            .filter { it.view.isAttachedToWindow }
            .forEach { it.measureAndLayoutForTest() }
    }
}
