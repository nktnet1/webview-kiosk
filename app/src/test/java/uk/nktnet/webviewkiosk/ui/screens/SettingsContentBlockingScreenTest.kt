package uk.nktnet.webviewkiosk.ui.screens

import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.navigation.NavController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import uk.nktnet.webviewkiosk.blocking.FilterListStore

@OptIn(ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w480dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsContentBlockingScreenTest {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var view: ComposeView
    private lateinit var recomposer: Recomposer
    private lateinit var store: FilterListStore
    private val roots = mutableListOf<ViewRootForTest>()
    private val clock = BroadcastFrameClock()
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
    private var frameTime = 0L

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "content-blocking-lists").deleteRecursively()
        store = FilterListStore(context)
        listOf("First", "Second", "Other").forEach { name ->
            store.importFrom(
                name,
                "||${name.lowercase()}.example^".byteInputStream(),
            )
        }
        ViewRootForTest.onViewCreatedCallback = { roots += it }
        activityController =
            Robolectric.buildActivity(AppCompatActivity::class.java)
                .setup()
                .visible()
        val activity = activityController.get()
        recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view = ComposeView(activity)
        view.setParentCompositionContext(recomposer)
        activity.setContentView(view)
        val navController = NavController(activity)
        view.setContent {
            MaterialTheme { SettingsContentBlockingScreen(navController) }
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
    fun defaultActionsPutManageFirstAndAddDefaultLast() {
        click("Actions")
        val expected =
            listOf(
                "Manage",
                "Enable all",
                "Disable all",
                "Refresh all",
                "Add Default",
            )
        val menu =
            nodes()
                .filter { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any {
                        it.text in expected
                    } == true
                }
                .sortedBy { it.boundsInWindow.top }
                .map { it.config[SemanticsProperties.Text].single().text }
        assertEquals(expected, menu)
        click("Manage")
        assertTrue(hasText("0 selected"))
        assertTrue(disabled(textNode("Actions")))
        assertEquals(ToggleableState.Off, selectionState("Select First"))
    }

    @Test
    fun selectedActionsOnlyChangeTheCheckedListsAndRemovalNeedsConfirmation() {
        enterManage()
        clickDescription("Select First")
        click("Actions")
        click("Disable selected")
        awaitCondition { !store.list().first().enabled && actionsReady() }
        assertEquals(listOf(false, true, true), store.list().map { it.enabled })
        assertTrue(hasText("1 selected"))
        assertEquals(ToggleableState.On, selectionState("Select First"))

        click("Actions")
        click("Enable selected")
        awaitCondition { store.list().all { it.enabled } && actionsReady() }
        clickDescription("Select Second")
        assertTrue(hasText("2 selected"))
        click("Actions")
        click("Remove selected")
        assertTrue(hasText("Remove 2 filter lists?"))
        assertEquals(3, store.list().size)
        click("Cancel")
        assertEquals(3, store.list().size)
        assertTrue(hasText("2 selected"))

        click("Actions")
        click("Remove selected")
        click("Remove")
        awaitCondition { store.list().size == 1 && hasText("0 selected") }
        assertEquals("Other", store.list().single().name)
        assertEquals(ToggleableState.Off, selectionState("Select Other"))
        assertTrue(disabled(textNode("Actions")))
    }

    @Test
    fun selectAllTogglesTheSelectionAndDoneClearsIt() {
        enterManage()
        clickDescription("Select First")
        assertEquals(
            ToggleableState.Indeterminate,
            selectionState("Select all filter lists"),
        )
        clickDescription("Select all filter lists")
        assertTrue(hasText("3 selected"))
        assertEquals(ToggleableState.On, selectionState("Select Other"))
        clickDescription("Select all filter lists")
        assertTrue(hasText("0 selected"))
        clickDescription("Select Second")
        click("Done")
        assertFalse(hasText("1 selected"))
        assertTrue(store.list().all { it.enabled })
        enterManage()
        assertTrue(hasText("0 selected"))
        assertEquals(ToggleableState.Off, selectionState("Select Second"))
        clickDescription("Back")
        assertTrue(hasText("Add list"))
    }

    private fun enterManage() {
        click("Actions")
        click("Manage")
    }

    private fun nodes(): List<SemanticsNode> =
        roots
            .filter { it.view.isAttachedToWindow }
            .flatMap {
                it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
            }

    private fun hasText(text: String): Boolean =
        nodes().any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any {
                it.text == text
            } == true
        }

    private fun textNode(text: String): SemanticsNode =
        nodes().first { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any {
                it.text == text
            } == true
        }

    private fun descriptionNode(description: String): SemanticsNode =
        nodes().first {
            it.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.contains(description) == true
        }

    private fun selectionState(description: String): ToggleableState =
        descriptionNode(description).config[SemanticsProperties.ToggleableState]

    private fun disabled(node: SemanticsNode): Boolean =
        generateSequence(node) { it.parent }
            .any {
                it.config.contains(SemanticsProperties.Disabled)
            }

    private fun actionsReady(): Boolean = !disabled(textNode("Actions"))

    private fun click(text: String) = clickNode(textNode(text))

    private fun clickDescription(description: String) =
        clickNode(descriptionNode(description))

    private fun clickNode(node: SemanticsNode) {
        val clickable =
            generateSequence(node) { it.parent }
                .first {
                    it.config.getOrNull(SemanticsActions.OnClick)?.action != null
                }
        assertFalse(disabled(clickable))
        assertTrue(clickable.config[SemanticsActions.OnClick].action!!.invoke())
        frames()
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            frames()
            Thread.sleep(5)
        }
        assertTrue("Settings operation did not finish", condition())
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
                .forEach {
                    it.measureAndLayoutForTest()
                }
        }
    }
}
