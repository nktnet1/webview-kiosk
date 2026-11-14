package uk.nktnet.webviewkiosk.ui.screens

import android.os.Looper
import android.text.format.Formatter
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
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
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.blocking.FilterListBatchProgress
import uk.nktnet.webviewkiosk.blocking.FilterListDownloadProgress
import uk.nktnet.webviewkiosk.blocking.FilterListDownloadStage
import uk.nktnet.webviewkiosk.blocking.FilterListStore
import uk.nktnet.webviewkiosk.blocking.FilterListType
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterLists

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
            val skipped =
                if (name == "First") {
                    $$"\n||popup.example^$popup\n||csp.example^$csp=script-src 'none'"
                } else {
                    ""
                }
            store.importFrom(
                name,
                "||${name.lowercase()}.example^$skipped".byteInputStream(),
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
        renderScreen()
    }

    private fun renderScreen() {
        val navController = NavController(activityController.get())
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
    fun batchDownloadStatusShowsCurrentNameBothProgressBarsAndByteCount() {
        view.setContent {
            MaterialTheme {
                FilterListDownloadStatus(
                    message = "Adding filter lists…",
                    progress =
                        FilterListBatchProgress(
                            name = "EasyList",
                            current = 2,
                            total = 4,
                            completed = 1,
                            download =
                                FilterListDownloadProgress(
                                    FilterListDownloadStage.DOWNLOADING,
                                    bytesRead = 512,
                                    totalBytes = 1024,
                                ),
                        ),
                )
            }
        }
        frames()
        assertTrue(hasText("EasyList"))
        assertTrue(hasText(message(R.string.ui_filter_list_progress_count, 2, 4)))
        assertTrue(
            hasText(
                message(
                    R.string.ui_filter_list_current_file_bytes,
                    Formatter.formatShortFileSize(activityController.get(), 512),
                    Formatter.formatShortFileSize(activityController.get(), 1024),
                )
            )
        )
        val bars =
            nodes().mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
            }
        assertEquals(listOf(0.25f, 0.5f), bars.map { it.current })
    }

    @Test
    fun unknownSingleDownloadStatusDoesNotInventAPercentage() {
        view.setContent {
            MaterialTheme {
                FilterListDownloadStatus(
                    message = "Downloading filter list…",
                    progress =
                        FilterListBatchProgress(
                            name = "Custom list",
                            current = 1,
                            total = 1,
                            completed = 0,
                            download =
                                FilterListDownloadProgress(
                                    FilterListDownloadStage.DOWNLOADING,
                                    bytesRead = 2048,
                                ),
                        ),
                )
            }
        }
        frames()
        assertTrue(hasText("Custom list"))
        assertFalse(hasText(message(R.string.ui_filter_list_progress_count, 1, 1)))
        assertTrue(
            hasText(
                message(
                    R.string.ui_filter_list_current_file_received,
                    Formatter.formatShortFileSize(activityController.get(), 2048),
                )
            )
        )
        val bars =
            nodes().mapNotNull {
                it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
            }
        assertEquals(1, bars.size)
        assertEquals(0f, bars.single().current)
    }

    @Test
    fun downloadStatusKeepsItsSizeAsBytesAndStagesChange() {
        val progress =
            mutableStateOf(
                FilterListBatchProgress(
                    name = "EasyList",
                    current = 1,
                    total = 3,
                    completed = 0,
                    download =
                        FilterListDownloadProgress(
                            FilterListDownloadStage.CONNECTING
                        ),
                )
            )
        view.setContent {
            MaterialTheme {
                FilterListDownloadStatus(
                    message = "Adding default filter lists…",
                    progress = progress.value,
                )
            }
        }
        frames()
        fun cardHeight() =
            nodes()
                .first {
                    it.config.getOrNull(SemanticsProperties.TestTag) ==
                        "filter_list_download_status"
                }
                .boundsInWindow
                .height

        val initialHeight = cardHeight()
        progress.value =
            progress.value.copy(
                download =
                    FilterListDownloadProgress(
                        stage = FilterListDownloadStage.DOWNLOADING,
                        bytesRead = 2048,
                        totalBytes = 4096,
                    )
            )
        frames()
        assertEquals(initialHeight, cardHeight())
        progress.value =
            progress.value.copy(
                name = "Peter Lowe’s Ad and tracking server list",
                current = 2,
                completed = 1,
                download =
                    FilterListDownloadProgress(FilterListDownloadStage.SAVING),
            )
        frames()
        assertEquals(initialHeight, cardHeight())
    }

    @Test
    fun presetPickerStartsWithAnAvailablePresetInsteadOfAnInstalledOne() {
        val installed = SuggestedFilterLists.all.first()
        val next = SuggestedFilterLists.all[1]
        store.importFrom(
            "${installed.name}.txt",
            "||ads.example^".byteInputStream(),
            installed.url,
            FilterListType.PRESET,
        )
        view.disposeComposition()
        renderScreen()

        click(message(R.string.ui_add_list))
        assertTrue(hasText(next.name))
        clickDescription(message(R.string.ui_preset))
        assertTrue(hasText(next.name))
        click(message(R.string.ui_cancel))
    }

    @Test
    fun listInfoExplainsSkippedRulesWithoutChangingTheList() {
        val saved = store.list()
        clickDescription(message(R.string.ui_options_for_value, "First"))
        click(message(R.string.ui_info))
        awaitCondition {
            hasText(message(R.string.option_display_filter_skip_reason_popup)) &&
                hasText(message(R.string.option_display_filter_skip_reason_csp))
        }
        assertTrue(hasText(message(R.string.ui_skipped_rules)))
        assertEquals(2, store.info(saved.first().id).skipReasons.values.sum())
        assertEquals(saved, store.list())
        click(message(R.string.ui_close))
    }

    @Test
    fun addDefaultActionConfirmsOnlyMissingPresetsAndCancelPreservesLists() {
        val preset = SuggestedFilterLists.defaults.first()
        store.importFrom(
            "Already added",
            "||ads.example^".byteInputStream(),
            preset.url,
            FilterListType.URL,
        )
        val saved = store.list()
        view.disposeComposition()
        renderScreen()
        click(message(R.string.ui_actions))
        click(message(R.string.ui_add_default))
        assertTrue(hasText(message(R.string.ui_add_default_lists)))
        assertTrue(hasText(message(R.string.ui_the_following_lists_will_be_added)))
        assertTrue(hasText(message(R.string.ui_bullet)))
        assertFalse(hasText(preset.name))
        SuggestedFilterLists.defaults.drop(1).forEach {
            assertTrue(hasText(it.name))
        }
        assertEquals(saved, store.list())
        click(message(R.string.ui_cancel))
        assertFalse(hasText(message(R.string.ui_add_default_lists)))
        assertEquals(saved, store.list())
    }

    @Test
    fun emptyListsOfferAddDefaultAndBothEntryPointsUseTheSameConfirmation() {
        enterManage()
        clickDescription(message(R.string.ui_select_all_filter_lists))
        click(message(R.string.ui_actions))
        click(message(R.string.ui_remove_selected))
        click(message(R.string.ui_remove))
        awaitCondition {
            store.list().isEmpty() &&
                hasText(message(R.string.ui_no_filter_lists_have_been_added)) &&
                !disabled(textNode(message(R.string.ui_done)))
        }
        click(message(R.string.ui_done))
        repeat(2) { entry ->
            if (entry == 1) {
                click(message(R.string.ui_actions))
            }
            click(message(R.string.ui_add_default))
            assertTrue(hasText(message(R.string.ui_add_default_lists)))
            SuggestedFilterLists.defaults.forEach { assertTrue(hasText(it.name)) }
            assertTrue(store.list().isEmpty())
            click(message(R.string.ui_cancel))
            assertTrue(store.list().isEmpty())
        }
    }

    @Test
    fun defaultActionsPutManageFirstAndAddDefaultLast() {
        click(message(R.string.ui_actions))
        val expected =
            listOf(
                message(R.string.ui_manage),
                message(R.string.ui_enable_all),
                message(R.string.ui_disable_all),
                message(R.string.ui_refresh_all),
                message(R.string.ui_add_default),
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
        click(message(R.string.ui_manage))
        assertTrue(hasText(message(R.string.ui_value_selected, "0")))
        assertTrue(disabled(textNode(message(R.string.ui_actions))))
        assertEquals(
            ToggleableState.Off,
            selectionState(message(R.string.ui_select_value, "First")),
        )
    }

    @Test
    fun selectedActionsOnlyChangeTheCheckedListsAndRemovalNeedsConfirmation() {
        enterManage()
        clickDescription(message(R.string.ui_select_value, "First"))
        click(message(R.string.ui_actions))
        click(message(R.string.ui_disable_selected))
        awaitCondition { !store.list().first().enabled && actionsReady() }
        assertEquals(listOf(false, true, true), store.list().map { it.enabled })
        assertTrue(hasText(message(R.string.ui_value_selected, "1")))
        assertEquals(
            ToggleableState.On,
            selectionState(message(R.string.ui_select_value, "First")),
        )

        click(message(R.string.ui_actions))
        click(message(R.string.ui_enable_selected))
        awaitCondition { store.list().all { it.enabled } && actionsReady() }
        clickDescription(message(R.string.ui_select_value, "Second"))
        assertTrue(hasText(message(R.string.ui_value_selected, "2")))
        click(message(R.string.ui_actions))
        click(message(R.string.ui_remove_selected))
        assertTrue(hasText(message(R.string.ui_remove_value_filter_lists, "2")))
        assertTrue(hasText(message(R.string.ui_bullet)))
        assertEquals(3, store.list().size)
        click(message(R.string.ui_cancel))
        assertEquals(3, store.list().size)
        assertTrue(hasText(message(R.string.ui_value_selected, "2")))

        click(message(R.string.ui_actions))
        click(message(R.string.ui_remove_selected))
        click(message(R.string.ui_remove))
        awaitCondition {
            store.list().size == 1 &&
                hasText(message(R.string.ui_value_selected, "0"))
        }
        assertEquals("Other", store.list().single().name)
        assertEquals(
            ToggleableState.Off,
            selectionState(message(R.string.ui_select_value, "Other")),
        )
        assertTrue(disabled(textNode(message(R.string.ui_actions))))
    }

    @Test
    fun togglingOneListDoesNotDisableTheOtherSwitches() {
        assertTrue(hasText(message(R.string.ui_lists_enabled_count, "3", "3")))
        clickDescription(message(R.string.ui_filter_value, "First"))
        assertFalse(
            disabled(descriptionNode(message(R.string.ui_filter_value, "Second")))
        )
        assertFalse(
            disabled(descriptionNode(message(R.string.ui_filter_value, "Other")))
        )
        awaitCondition {
            !store.list().first().enabled &&
                selectionState(message(R.string.ui_filter_value, "First")) ==
                    ToggleableState.Off &&
                hasText(message(R.string.ui_lists_enabled_count, "2", "3"))
        }

        clickDescription(message(R.string.ui_filter_value, "Second"))
        awaitCondition {
            !store.list()[1].enabled &&
                selectionState(message(R.string.ui_filter_value, "Second")) ==
                    ToggleableState.Off &&
                hasText(message(R.string.ui_lists_enabled_count, "1", "3"))
        }
        assertTrue(store.list()[2].enabled)
    }

    @Test
    fun listInfoShowsSourceFirstAndExplicitAmPmForRefreshTime() {
        val url = "https://example.org/rules.txt"
        store.importFrom("Remote", "||ads.example^".byteInputStream(), url)
        view.disposeComposition()
        renderScreen()
        clickDescription(message(R.string.ui_options_for_value, "Remote"))
        click(message(R.string.ui_info))
        awaitCondition { hasText(message(R.string.ui_source_url)) && hasText(url) }

        assertTrue(
            textNode(message(R.string.ui_source_url)).boundsInWindow.top <
                textNode(message(R.string.ui_list_details)).boundsInWindow.top
        )
        assertTrue(hasText(message(R.string.ui_last_updated)))
        assertFalse(hasText("Refreshed"))
        assertTrue(
            textNode(message(R.string.ui_file_size)).boundsInWindow.top <
                textNode(message(R.string.ui_last_updated)).boundsInWindow.top
        )
        assertTrue(
            nodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { text ->
                    Regex("""\d{1,2}:\d{2} [AP]M""").containsMatchIn(text.text)
                } == true
            }
        )
    }

    @Test
    fun listInfoSignalsMoreContentUntilScrolledToBottom() {
        clickDescription(message(R.string.ui_options_for_value, "First"))
        click(message(R.string.ui_info))
        awaitCondition { hasText(message(R.string.ui_scroll_for_more)) }

        val scrollable =
            generateSequence(textNode(message(R.string.ui_list_details))) {
                    it.parent
                }
                .first { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
        val hint = textNode(message(R.string.ui_scroll_for_more))
        assertTrue(
            "Scroll hint should overlay the bottom of the details",
            hint.boundsInWindow.top < scrollable.boundsInWindow.bottom,
        )
        assertTrue(
            "Scroll hint should remain within the details area",
            hint.boundsInWindow.bottom <= scrollable.boundsInWindow.bottom,
        )
        assertTrue(
            "Scroll hint overlaps Close",
            hint.boundsInWindow.bottom <=
                textNode(message(R.string.ui_close)).boundsInWindow.top,
        )

        assertTrue(
            scrollable.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 10000f)
        )
        awaitCondition { !hasText(message(R.string.ui_scroll_for_more)) }
        assertTrue(hasText(message(R.string.ui_skipped_rules)))
        val gap =
            textNode(message(R.string.ui_close)).boundsInWindow.top -
                scrollable.boundsInWindow.bottom
        val density = activityController.get().resources.displayMetrics.density
        assertTrue(
            "Too much space above Close: ${gap / density}dp",
            gap <= 28f * density,
        )
    }

    @Test
    @Config(qualifiers = "w480dp-h640dp")
    fun listInfoDialogLeavesSpaceAroundSystemBarsOnShortScreens() {
        clickDescription(message(R.string.ui_options_for_value, "First"))
        click(message(R.string.ui_info))
        awaitCondition {
            hasText(message(R.string.ui_list_details)) &&
                hasText(message(R.string.ui_close))
        }
        val density = activityController.get().resources.displayMetrics.density
        val height = activityController.get().resources.displayMetrics.heightPixels
        val dialog =
            nodes().first { it.config.contains(SemanticsProperties.IsDialog) }
        val title =
            nodes()
                .filter { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any {
                        it.text == "First"
                    } == true
                }
                .minBy { it.boundsInWindow.top }
        val close = textNode(message(R.string.ui_close))
        assertTrue(
            title.boundsInWindow.top - dialog.boundsInWindow.top >= 24f * density
        )
        assertTrue(
            dialog.boundsInWindow.bottom - close.boundsInWindow.bottom >=
                24f * density
        )
        assertTrue(dialog.boundsInWindow.bottom <= height - 16f * density)
    }

    @Test
    fun selectAllTogglesTheSelectionAndDoneClearsIt() {
        enterManage()
        clickDescription(message(R.string.ui_select_value, "First"))
        assertEquals(
            ToggleableState.Indeterminate,
            selectionState(message(R.string.ui_select_all_filter_lists)),
        )
        clickDescription(message(R.string.ui_select_all_filter_lists))
        assertTrue(hasText(message(R.string.ui_value_selected, "3")))
        assertEquals(
            ToggleableState.On,
            selectionState(message(R.string.ui_select_value, "Other")),
        )
        clickDescription(message(R.string.ui_select_all_filter_lists))
        assertTrue(hasText(message(R.string.ui_value_selected, "0")))
        clickDescription(message(R.string.ui_select_value, "Second"))
        click(message(R.string.ui_done))
        assertFalse(hasText(message(R.string.ui_value_selected, "1")))
        assertTrue(store.list().all { it.enabled })
        enterManage()
        assertTrue(hasText(message(R.string.ui_value_selected, "0")))
        assertEquals(
            ToggleableState.Off,
            selectionState(message(R.string.ui_select_value, "Second")),
        )
        clickDescription(message(R.string.ui_back))
        assertTrue(hasText(message(R.string.ui_add_list)))
    }

    private fun enterManage() {
        click(message(R.string.ui_actions))
        click(message(R.string.ui_manage))
    }

    private fun message(id: Int, vararg args: Any) =
        activityController.get().getString(id, *args)

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

    private fun actionsReady(): Boolean =
        !disabled(textNode(message(R.string.ui_actions)))

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
