package uk.nktnet.webviewkiosk.ui.components.webview

import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import uk.nktnet.webviewkiosk.adblock.KotlinFilterEngine
import uk.nktnet.webviewkiosk.blocking.BlockingStatus
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
import uk.nktnet.webviewkiosk.blocking.FilterListStore
import uk.nktnet.webviewkiosk.ui.components.common.MinimumLoadingState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ContentBlockingGateTest {
    private lateinit var controller: ActivityController<AppCompatActivity>
    private lateinit var view: ComposeView
    private lateinit var recomposer: Recomposer
    private lateinit var store: FilterListStore
    private lateinit var blocker: ContentBlockingService
    private val worker = QueuedExecutor()
    private val clock = BroadcastFrameClock()
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
    private val loading = MinimumLoadingState(now = { 0L }, pause = {})
    private val showPage = mutableStateOf(false)
    private val createdWithRules = mutableListOf<Boolean>()
    private var mounted = false
    private var frameNanos = 0L

    @Before
    fun setUp() {
        controller =
            Robolectric.buildActivity(AppCompatActivity::class.java)
                .setup()
                .visible()
        val activity = controller.get()
        activity
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
        java.io.File(activity.filesDir, "content-blocking-lists").deleteRecursively()
        store = FilterListStore(activity)
        store.importFrom(
            "Test",
            "||ads.example^\nsite.example##.sponsor".byteInputStream(),
        )
        blocker = ContentBlockingService(store, KotlinFilterEngine(), worker)
        recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view = ComposeView(activity)
        view.setParentCompositionContext(recomposer)
        activity.setContentView(view)
        view.setContent {
            MaterialTheme {
                if (showPage.value) {
                    ContentBlockingGate(blocker, loading) {
                        DisposableEffect(Unit) {
                            mounted = true
                            createdWithRules +=
                                blocker.shouldBlock(
                                    "https://ads.example/banner",
                                    "https://site.example/",
                                    "image",
                                    "GET",
                                )
                            onDispose { mounted = false }
                        }
                        Text("WebView content")
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        controller.pause().stop().destroy()
    }

    @Test
    fun returningWhileEnablementCompilesDefersPageCreation() {
        blocker.setEnabled(false)
        blocker.start()
        blocker.setEnabled(true)
        showPage.value = true
        frames()

        assertEquals(BlockingStatus.LOADING, blocker.status.value)
        assertFalse(mounted)
        assertTrue(createdWithRules.isEmpty())
        assertNotNull(loading.message.value)

        worker.runAll()
        frames()
        assertTrue(mounted)
        assertEquals(listOf(true), createdWithRules)
        assertTrue(blocker.hasActiveCosmeticRules)
        assertNull(loading.message.value)
    }

    @Test
    fun alreadyCompiledRulesDoNotShowLoadingOrCompileAgain() {
        blocker.start()
        worker.runAll()
        showPage.value = true
        frames()

        assertEquals(listOf(true), createdWithRules)
        assertTrue(mounted)
        assertTrue(worker.tasks.isEmpty())
        assertNull(loading.message.value)
    }

    @Test
    fun aSettingChangeReplacesTheVisiblePageAfterCompilation() {
        blocker.setEnabled(false)
        blocker.start()
        showPage.value = true
        frames()
        assertEquals(listOf(false), createdWithRules)

        blocker.setEnabled(true)
        frames()
        assertFalse(mounted)
        assertEquals(listOf(false), createdWithRules)
        worker.runAll()
        frames()
        assertEquals(listOf(false, true), createdWithRules)

        blocker.setEnabled(false)
        frames()
        assertTrue(mounted)
        assertEquals(listOf(false, true, false), createdWithRules)
    }

    @Test
    fun aLoadFailureReleasesThePageAndKeepsTheErrorAvailable() {
        blocker =
            ContentBlockingService(
                store,
                KotlinFilterEngine(),
                Executor { throw RejectedExecutionException("Loader unavailable") },
            )
        showPage.value = true
        frames()

        assertTrue(mounted)
        assertEquals(listOf(false), createdWithRules)
        assertEquals(BlockingStatus.ERROR, blocker.status.value)
        assertNotNull(blocker.lastError.value)
        assertNull(loading.message.value)
    }

    private fun frames() {
        repeat(12) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            frameNanos += 16_000_000L
            clock.sendFrame(frameNanos)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private class QueuedExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().run()
            }
        }
    }
}
