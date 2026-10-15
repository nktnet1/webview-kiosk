package uk.nktnet.webviewkiosk.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.ArrayDeque
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
class ContentBlockingServiceTest {
    private lateinit var store: FilterListStore
    private lateinit var engine: FakeEngine
    private lateinit var executor: QueuedExecutor
    private lateinit var service: ContentBlockingService

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("content_blocking_lists", 0).edit().clear().commit()
        java.io.File(context.filesDir, "content-blocking-lists").deleteRecursively()
        store = FilterListStore(context, validator = FilterListValidator {})
        engine = FakeEngine()
        executor = QueuedExecutor()
        service = ContentBlockingService(store, engine, executor)
    }

    @Test
    fun noImportDoesNotCompileRules() {
        service.start()
        executor.runAll()
        assertEquals(0, engine.replacements)
        assertEquals(1, engine.clears)
        assertFalse(service.shouldBlock("https://ads.example/pixel", "https://site.example", "other", "GET"))
        assertNull(service.lastError.value)
    }

    @Test
    fun compilationOccursOutsideRequestAndIsReplacedAfterUpdate() {
        val item = store.importFrom("test.txt", "||ads.example^".byteInputStream())
        service.start()
        assertFalse(service.shouldBlock("https://ads.example/pixel", "https://site.example", "other", "GET"))
        executor.runAll()
        assertEquals(1, engine.replacements)
        assertEquals(BlockingStatus.ACTIVE, service.status.value)
        assertTrue(service.shouldBlock("https://ads.example/pixel", "https://site.example", "other", "GET"))

        store.setEnabled(item.id, false)
        service.reload()
        assertTrue(service.shouldBlock("https://ads.example/pixel", "https://site.example", "other", "GET"))
        executor.runAll()
        assertEquals(1, engine.clears)
        assertEquals(BlockingStatus.DISABLED, service.status.value)
        assertFalse(service.shouldBlock("https://ads.example/pixel", "https://site.example", "other", "GET"))
    }

    @Test
    fun supersededReloadDoesNotRestoreStaleRules() {
        val item = store.importFrom("test.txt", "||ads.example^".byteInputStream())
        service.start()
        store.setEnabled(item.id, false)
        service.reload()
        executor.runAll()
        assertEquals(0, engine.replacements)
        assertEquals(1, engine.clears)
    }

    @Test
    fun engineLoadFailureLeavesRequestsAllowed() {
        store.importFrom("test.txt", "||ads.example^".byteInputStream())
        engine.failReplace = true
        service.start()
        executor.runAll()
        assertFalse(service.shouldBlock("https://ads.example", "https://site.example", "other", "GET"))
        assertTrue(service.lastError.value!!.contains("failed"))
        assertEquals(BlockingStatus.ERROR, service.status.value)
        assertTrue(service.initialLoadComplete.value)
    }

    @Test
    fun firstNavigationCanWaitWithoutBlockingTheLoader() = runBlocking {
        store.importFrom("test.txt", "||ads.example^".byteInputStream())
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { service.awaitInitialLoad() }
        assertFalse(waiting.isCompleted)
        assertFalse(service.initialLoadComplete.value)
        executor.runAll()
        waiting.await()
        assertTrue(service.initialLoadComplete.value)
        assertTrue(service.hasActiveRules)
    }

    @Test
    fun failedReloadKeepsWorkingRulesAndReportsTheError() {
        store.importFrom("test.txt", "||ads.example^".byteInputStream())
        service.start()
        executor.runAll()
        engine.failReplace = true
        service.reload()
        assertTrue(service.hasActiveRules)
        executor.runAll()
        assertTrue(service.shouldBlock("https://ads.example/pixel", "https://site.example", "other", "GET"))
        assertEquals(BlockingStatus.ERROR, service.status.value)
        assertTrue(service.lastError.value!!.contains("failed"))
    }

    @Test
    fun skippedRuleCountTracksOnlyTheCommittedSnapshotAndClearsWhenDisabled() {
        val item = store.importFrom("test.txt", "||ads.example^".byteInputStream())
        engine.skipped = 2
        service.start()
        executor.runAll()
        assertEquals(2, service.skippedRules.value)
        engine.skipped = 99
        engine.failReplace = true
        service.reload()
        executor.runAll()
        assertEquals(2, service.skippedRules.value)
        store.setEnabled(item.id, false)
        service.reload()
        executor.runAll()
        assertEquals(0, service.skippedRules.value)
    }

    @Test
    fun rejectedLoaderReleasesTheStartupGate() = runBlocking {
        val rejected = ContentBlockingService(store, engine, Executor { throw java.util.concurrent.RejectedExecutionException("closed") })
        rejected.awaitInitialLoad()
        assertEquals(BlockingStatus.ERROR, rejected.status.value)
        assertFalse(rejected.hasActiveRules)
    }

    private class FakeEngine : NetworkFilterEngine {
        var replacements = 0
        var clears = 0
        var failReplace = false
        var skipped = 0
        private var list = ""
        override fun replace(lists: List<String>) {
            if (failReplace) throw IllegalStateException("failed to compile")
            replacements++
            list = lists.joinToString("\n")
        }
        override fun clear() { clears++; list = ""; skipped = 0 }
        override fun unsupportedRuleCount(): Int = skipped
        override fun shouldBlock(url: String, sourceUrl: String, type: String, method: String) =
            list.contains("||ads.example^") && url.contains("ads.example")
    }

    private class QueuedExecutor : Executor {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.addLast(command) }
        fun runAll() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }
}
