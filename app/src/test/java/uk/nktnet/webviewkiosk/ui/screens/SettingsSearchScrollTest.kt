package uk.nktnet.webviewkiosk.ui.screens

import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.NavController
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.ui.components.setting.LocalSettingsSearchTarget
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
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)

    @Before
    fun setUp() {
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
                        SettingsSearchTarget(title, {})
                ) {
                    SettingsAppearanceScreen(NavController(activity))
                }
            }
        }
        repeat(30) { frame -> renderFrame(frame, advanceTime = false) }

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
        val activity = controller.get()
        val title =
            activity.getString(R.string.appearance_show_loading_progress_bar_title)
        var consumed = 0
        view.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalSettingsSearchTarget provides
                        SettingsSearchTarget(title) { consumed++ }
                ) {
                    SettingsAppearanceScreen(NavController(activity))
                }
            }
        }

        repeat(15) { frame -> renderFrame(frame, advanceTime = true) }
        assertEquals("Do not flash before the scroll and delay", 0, consumed)

        repeat(130) { frame -> renderFrame(frame + 15, advanceTime = true) }
        assertEquals(
            "The highlight must finish and consume its request",
            1,
            consumed,
        )

        repeat(20) { frame -> renderFrame(frame + 145, advanceTime = true) }
        assertEquals("The same selection must not flash again", 1, consumed)
    }

    private fun renderFrame(frame: Int, advanceTime: Boolean) {
        Snapshot.sendApplyNotifications()
        if (advanceTime) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        } else {
            shadowOf(Looper.getMainLooper()).idle()
        }
        clock.sendFrame((frame + 1) * 16_000_000L)
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
