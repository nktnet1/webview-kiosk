package uk.nktnet.webviewkiosk.blocking

import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.adblock.FilterListValidator
import uk.nktnet.webviewkiosk.adblock.NetworkFilterEngine
import uk.nktnet.webviewkiosk.adblock.RuleLoadStage

@RunWith(RobolectricTestRunner::class)
class ContentBlockingServiceTest {
    private lateinit var store: FilterListStore
    private lateinit var engine: FakeEngine
    private lateinit var executor: QueuedExecutor
    private lateinit var service: ContentBlockingService

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
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
        assertFalse(
            service.shouldBlock(
                "https://ads.example/pixel",
                "https://site.example",
                "other",
                "GET",
            )
        )
        assertNull(service.lastError.value)
        assertNull(service.loadStage.value)
    }

    @Test
    fun reportsTheActualLoadPhaseAndClearsItOnCompletionOrFailure() {
        val item = store.importFrom("test.txt", "||ads.example^".byteInputStream())
        service.start()
        assertEquals(RuleLoadStage.READING_LISTS, service.loadStage.value)
        engine.duringReplace = {
            assertEquals(RuleLoadStage.COMPILING, service.loadStage.value)
        }
        executor.runAll()
        assertNull(service.loadStage.value)
        engine.failReplace = true
        service.reload()
        executor.runAll()
        assertEquals(BlockingStatus.ERROR, service.status.value)
        assertNull(service.loadStage.value)
        store.setEnabled(item.id, false)
        engine.duringClear = {
            assertEquals(RuleLoadStage.CLEARING, service.loadStage.value)
        }
        service.reload()
        executor.runAll()
        assertNull(service.loadStage.value)
    }

    @Test
    fun compilationOccursOutsideRequestAndIsReplacedAfterUpdate() {
        val item = store.importFrom("test.txt", "||ads.example^".byteInputStream())
        service.start()
        assertFalse(
            service.shouldBlock(
                "https://ads.example/pixel",
                "https://site.example",
                "other",
                "GET",
            )
        )
        executor.runAll()
        assertEquals(1, engine.replacements)
        assertEquals(BlockingStatus.ACTIVE, service.status.value)
        assertTrue(
            service.shouldBlock(
                "https://ads.example/pixel",
                "https://site.example",
                "other",
                "GET",
            )
        )

        store.setEnabled(item.id, false)
        service.reload()
        assertTrue(
            service.shouldBlock(
                "https://ads.example/pixel",
                "https://site.example",
                "other",
                "GET",
            )
        )
        executor.runAll()
        assertEquals(1, engine.clears)
        assertEquals(BlockingStatus.DISABLED, service.status.value)
        assertFalse(
            service.shouldBlock(
                "https://ads.example/pixel",
                "https://site.example",
                "other",
                "GET",
            )
        )
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
        assertFalse(
            service.shouldBlock(
                "https://ads.example",
                "https://site.example",
                "other",
                "GET",
            )
        )
        assertTrue(service.lastError.value!!.contains("failed"))
        assertEquals(BlockingStatus.ERROR, service.status.value)
        assertTrue(service.initialLoadComplete.value)
    }

    @Test
    fun firstNavigationCanWaitWithoutBlockingTheLoader() = runBlocking {
        store.importFrom("test.txt", "||ads.example^".byteInputStream())
        val waiting =
            async(start = CoroutineStart.UNDISPATCHED) { service.awaitInitialLoad() }
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
        assertTrue(
            service.shouldBlock(
                "https://ads.example/pixel",
                "https://site.example",
                "other",
                "GET",
            )
        )
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
        val rejected =
            ContentBlockingService(
                store,
                engine,
                Executor {
                    throw java.util.concurrent.RejectedExecutionException("closed")
                },
            )
        rejected.awaitInitialLoad()
        assertEquals(BlockingStatus.ERROR, rejected.status.value)
        assertFalse(rejected.hasActiveRules)
    }

    @Test
    fun reloadCompletionWaitsUntilReplacementRulesAreActive() = runBlocking {
        store.importFrom("test.txt", "||ads.example^".byteInputStream())
        val revision = service.reload()
        val waiting =
            async(start = CoroutineStart.UNDISPATCHED) {
                service.awaitReload(revision)
            }
        assertFalse(waiting.isCompleted)
        assertEquals(BlockingStatus.LOADING, service.status.value)
        executor.runAll()
        waiting.await()
        assertEquals(BlockingStatus.ACTIVE, service.status.value)
        assertTrue(
            service.shouldBlock(
                "https://ads.example/a",
                "https://site.example/",
                "other",
                "GET",
            )
        )
    }

    @Test
    fun supersededWaitersFollowTheLatestReloadInsteadOfReportingEarlySuccess() =
        runBlocking {
            val item =
                store.importFrom("test.txt", "||ads.example^".byteInputStream())
            val first = service.reload()
            val firstWaiter =
                async(start = CoroutineStart.UNDISPATCHED) {
                    service.awaitReload(first)
                }
            store.setAllEnabled(false)
            val latest = service.reload()
            val latestWaiter =
                async(start = CoroutineStart.UNDISPATCHED) {
                    service.awaitReload(latest)
                }
            executor.runNext()
            assertFalse(firstWaiter.isCompleted)
            assertFalse(latestWaiter.isCompleted)
            executor.runAll()
            firstWaiter.await()
            latestWaiter.await()
            assertEquals(BlockingStatus.DISABLED, service.status.value)
            assertEquals(0, engine.replacements)
            assertFalse(store.info(item.id).item.enabled)
        }

    @Test
    fun completedOldRevisionCannotFinishWhileANewerLoadIsPending() = runBlocking {
        val first = service.reload()
        executor.runAll()
        service.reload()
        val waiting =
            async(start = CoroutineStart.UNDISPATCHED) { service.awaitReload(first) }
        assertFalse(waiting.isCompleted)
        executor.runAll()
        waiting.await()
        assertEquals(BlockingStatus.DISABLED, service.status.value)
    }

    @Test
    fun awaitedFailureIsReportedAndTheLastWorkingSnapshotRemainsActive() =
        runBlocking {
            store.importFrom("test.txt", "||ads.example^".byteInputStream())
            service.start()
            executor.runAll()
            engine.failReplace = true
            val revision = service.reload()
            val waiting =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { service.awaitReload(revision) }
                }
            executor.runAll()
            val result = waiting.await()
            assertTrue(result.isFailure)
            assertTrue(
                result
                    .exceptionOrNull()!!
                    .message!!
                    .contains("rules could not be loaded")
            )
            assertEquals(BlockingStatus.ERROR, service.status.value)
            assertTrue(
                service.shouldBlock(
                    "https://ads.example/a",
                    "https://site.example/",
                    "other",
                    "GET",
                )
            )
        }

    @Test
    fun rejectedReloadCompletesItsWaiterWithAnError() = runBlocking {
        val rejected =
            ContentBlockingService(
                store,
                engine,
                Executor {
                    throw java.util.concurrent.RejectedExecutionException("closed")
                },
            )
        val revision = rejected.reload()
        assertTrue(runCatching { rejected.awaitReload(revision) }.isFailure)
        assertEquals(BlockingStatus.ERROR, rejected.status.value)
    }

    @Test
    fun cancellingTheScreenWaitDoesNotCancelTheQueuedRuleLoad() = runBlocking {
        store.importFrom("test.txt", "||ads.example^".byteInputStream())
        val revision = service.reload()
        val waiting =
            async(start = CoroutineStart.UNDISPATCHED) {
                service.awaitReload(revision)
            }
        waiting.cancel()
        executor.runAll()
        waiting.join()
        assertEquals(BlockingStatus.ACTIVE, service.status.value)
        assertTrue(service.hasActiveRules)
    }

    @Test
    fun enabledSettingIsDistinctFromWhetherListsAreActive() {
        service.start()
        executor.runAll()
        assertEquals(BlockingStatus.DISABLED, service.status.value)
        assertTrue(service.enabled.value)

        service.setEnabled(false)
        assertFalse(service.enabled.value)
        service.setEnabled(true)
        executor.runAll()
        assertEquals(BlockingStatus.DISABLED, service.status.value)
        assertTrue(service.enabled.value)
    }

    @Test
    fun disabledStartupAndListChangesDoNotCompileOrWait() = runBlocking {
        val item = store.importFrom("test.txt", "||ads.example^".byteInputStream())
        service.setEnabled(false)
        service.start()
        service.awaitInitialLoad()
        service.awaitReload(service.reload())
        executor.runAll()
        assertEquals(0, engine.replacements)
        assertEquals(BlockingStatus.DISABLED, service.status.value)
        assertTrue(store.list().single { it.id == item.id }.enabled)

        service.setEnabled(true)
        executor.runAll()
        assertEquals(1, engine.replacements)
        assertTrue(service.hasActiveRules)
    }

    @Test
    fun disablingDuringCompilationPreventsItsResultFromReactivatingBlocking() {
        store.importFrom("test.txt", "||ads.example^".byteInputStream())
        engine.duringReplace = { service.setEnabled(false) }
        service.start()
        executor.runAll()
        assertEquals(BlockingStatus.DISABLED, service.status.value)
        assertFalse(service.hasActiveRules)
        assertTrue(service.initialLoadComplete.value)
        assertNull(service.lastError.value)
    }

    private class FakeEngine : NetworkFilterEngine {
        var replacements = 0
        var clears = 0
        var failReplace = false
        var skipped = 0
        var duringReplace: () -> Unit = {}
        var duringClear: () -> Unit = {}
        private var list = ""

        override fun replace(lists: List<String>) {
            duringReplace()
            if (failReplace) {
                throw IllegalStateException("failed to compile")
            }
            replacements++
            list = lists.joinToString("\n")
        }

        override fun clear() {
            duringClear()
            clears++
            list = ""
            skipped = 0
        }

        override fun unsupportedRuleCount(): Int = skipped

        override fun shouldBlock(
            url: String,
            sourceUrl: String,
            type: String,
            method: String,
        ) = list.contains("||ads.example^") && url.contains("ads.example")
    }

    private class QueuedExecutor : Executor {
        val queue = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            queue.addLast(command)
        }

        fun runNext() {
            queue.removeFirst().run()
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }
}
