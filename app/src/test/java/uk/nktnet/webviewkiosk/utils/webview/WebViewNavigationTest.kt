package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.config.HistoryEntry
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebViewNavigationTest {
    private lateinit var settings: SystemSettings
    private lateinit var userSettings: UserSettings
    private val first = "https://example.com/first"
    private val second = "https://example.com/second"
    private val third = "https://example.com/third"
    private val next = "https://example.com/next"
    private val loaded = mutableListOf<String>()

    @Before
    fun setUp() {
        settings = SystemSettings(RuntimeEnvironment.getApplication())
        settings.clearHistory()
        settings.urlBeforeNavigation = ""
        userSettings = UserSettings(RuntimeEnvironment.getApplication())
        userSettings.homeUrl = first
        userSettings.clearHistoryOnHome = false
        userSettings.allowRefresh = true
        loaded.clear()
    }

    @Test
    fun backAndForwardSelectTheEntryBeforeInvokingTheLoader() {
        seed()
        val observedIndices = mutableListOf<Int>()
        val loader: (String) -> Unit = {
            loaded.add(it)
            observedIndices.add(settings.historyIndex)
        }

        WebViewNavigation.goBack(loader, settings)
        WebViewNavigation.goForward(loader, settings)

        assertEquals(listOf(second, third), loaded)
        assertEquals(listOf(1, 2), observedIndices)
        assertHistory(2, first, second, third)
    }

    @Test
    fun traversalToAnExistingUrlDoesNotAddAnEntryOrDiscardForwardHistory() {
        seed()
        val entries = settings.historyStack
        WebViewNavigation.goBack(loaded::add, settings)
        append(second)
        append(second)

        assertEquals(entries, settings.historyStack)
        assertEquals(1, settings.historyIndex)
        WebViewNavigation.goForward(loaded::add, settings)
        append(third)
        assertHistory(2, first, second, third)
    }

    @Test
    fun traversalDoesNotSuppressAnUnrelatedHistoryUpdate() {
        seed()
        WebViewNavigation.goBack(loaded::add, settings)
        val anotherWrapper = SystemSettings(RuntimeEnvironment.getApplication())
        anotherWrapper.urlBeforeNavigation = second

        WebViewNavigation.appendWebviewHistory(anotherWrapper, next, next, true)

        assertHistory(2, first, second, next)
    }

    @Test
    fun aRedirectAsTheFirstTraversalCallbackUpdatesTheTargetAndPreservesForwardHistory() {
        seed()
        val target = settings.historyStack[1]
        WebViewNavigation.goBack(loaded::add, settings)
        settings.urlBeforeNavigation = second

        append(next, second, traversalEntryId = target.id)

        assertHistory(1, first, next, third)
        assertEquals(target.copy(url = next), settings.historyStack[1])
    }

    @Test
    fun aTraversalRedirectChainKeepsTheEntryIdentityTimestampAndForwardBranch() {
        seed()
        val target = settings.historyStack[1]
        val session = WebViewNavigationSession { true }
        WebViewNavigation.goBack({ url ->
            val entry = settings.historyStack[settings.historyIndex]
            session.beginLoad(url, entry.id, entry.url)
        }, settings)
        settings.urlBeforeNavigation = second

        commit(session, second, second)
        commit(session, next, second)
        commit(session, "$next/final", second)
        commit(session, "$next/final", second)

        assertHistory(1, first, "$next/final", third)
        assertEquals(target.copy(url = "$next/final"), settings.historyStack[1])
    }

    @Test
    fun disablingRedirectReplacementAppendsAndTruncatesTheForwardBranch() {
        seed(index = 1)
        val selected = settings.historyStack[1]
        append(next, second, replace = false, traversalEntryId = selected.id)

        assertHistory(2, first, second, next)
        assertEquals(selected, settings.historyStack[1])
    }

    @Test
    fun aStaleTraversalEntryCannotReplaceTheNewCurrentEntry() {
        seed(index = 0)
        val staleEntryId = settings.historyStack[0].id
        settings.historyIndex = 1
        settings.urlBeforeNavigation = second
        append(next, first, traversalEntryId = staleEntryId)

        assertHistory(2, first, second, next)
    }

    @Test
    fun normalRedirectReplacementPreservesTheNewEntriesMetadata() {
        seed()
        settings.urlBeforeNavigation = third
        append(next, next)
        val entry = settings.historyStack.last()
        append("$next/final", next)

        assertHistory(3, first, second, third, "$next/final")
        assertEquals(entry.copy(url = "$next/final"), settings.historyStack.last())
    }

    @Test
    fun aSpaPushFromTheOriginalDocumentAppendsInsteadOfReplacingIt() {
        seed(index = 0)
        settings.urlBeforeNavigation = first
        append(next, first)

        assertHistory(1, first, next)
    }

    @Test
    fun consecutiveSpaPushesDuringATraversalAppendInsteadOfCollapsingTheSelectedEntry() {
        seed(index = 0)
        val selected = settings.historyStack[0]
        val session = WebViewNavigationSession { true }
        session.beginLoad(first, selected.id, selected.url)
        commit(session, first, first, nativeHistoryIndex = 4)
        settings.urlBeforeNavigation = first
        commit(session, next, first, nativeHistoryIndex = 5)
        commit(session, "$next/second", first, nativeHistoryIndex = 6)

        assertHistory(2, first, next, "$next/second")
        assertEquals(selected, settings.historyStack[0])
    }

    @Test
    fun anInvalidOriginalUrlCannotReplaceHistory() {
        seed()
        append(next, "not a URL")
        assertHistory(3, first, second, third, next)
    }

    @Test
    fun repeatedNotificationsForTheCurrentUrlPreserveItsMetadata() {
        seed()
        val entries = settings.historyStack
        repeat(3) { append("$third/") }

        assertEquals(entries, settings.historyStack)
        assertHistory(2, first, second, third)
    }

    @Test
    fun aNewNavigationAfterBackTruncatesOnlyTheForwardBranch() {
        seed()
        val retained = settings.historyStack.take(2)
        WebViewNavigation.goBack(loaded::add, settings)
        settings.urlBeforeNavigation = second
        append(next, next)

        assertEquals(retained, settings.historyStack.take(2))
        assertHistory(2, first, second, next)
    }

    @Test
    fun aFailedTraversalRestoresTheCursorAndDoesNotPoisonTheNextNavigation() {
        seed()
        val failure = IllegalStateException("load failed")
        val thrown = runCatching {
            WebViewNavigation.goBack({ throw failure }, settings)
        }.exceptionOrNull()

        assertSame(failure, thrown)
        assertEquals(2, settings.historyIndex)
        append(next)
        assertHistory(3, first, second, third, next)
    }

    @Test
    fun aFailedLoaderDoesNotRollBackANewerReentrantNavigation() {
        seed()
        val failure = IllegalStateException("old load failed")
        val thrown = runCatching {
            WebViewNavigation.goBack({
                WebViewNavigation.navigateToIndex(loaded::add, settings, 0)
                throw failure
            }, settings)
        }.exceptionOrNull()

        assertSame(failure, thrown)
        assertEquals(listOf(first), loaded)
        assertHistory(0, first, second, third)
    }

    @Test
    fun rapidTraversalsIgnoreHistoryCallbacksForTheSupersededLoad() {
        seed()
        val session = WebViewNavigationSession { true }
        val loader: (String) -> Unit = { url ->
            val entry = settings.historyStack[settings.historyIndex]
            session.beginLoad(url, entry.id, entry.url)
        }

        WebViewNavigation.goBack(loader, settings)
        WebViewNavigation.goBack(loader, settings)
        WebViewNavigation.goForward(loader, settings)
        commit(session, first, first)
        commit(session, third, third)
        assertHistory(1, first, second, third)
        commit(session, next, second)
        assertHistory(1, first, next, third)
    }

    @Test
    fun removingAnEntryBeforeTheTraversalTargetKeepsTheTargetIdentity() {
        seed(index = 1)
        val target = settings.historyStack[1]
        WebViewNavigation.removeHistoryAtIndex(settings, 0)
        settings.urlBeforeNavigation = second
        append(next, second, traversalEntryId = target.id)

        assertHistory(0, next, third)
        assertEquals(target.copy(url = next), settings.historyStack[0])
    }

    @Test
    fun removingTheCurrentEntryOrAnInvalidIndexDoesNothing() {
        seed(index = 1)
        val entries = settings.historyStack
        for (index in listOf(1, -1, 3, Int.MAX_VALUE)) {
            WebViewNavigation.removeHistoryAtIndex(settings, index)
        }

        assertEquals(entries, settings.historyStack)
        assertEquals(1, settings.historyIndex)
    }

    @Test
    fun removingAForwardEntryDoesNotMoveTheCursor() {
        seed(index = 1)
        WebViewNavigation.removeHistoryAtIndex(settings, 2)
        assertHistory(1, first, second)
    }

    @Test
    fun clearingHistoryKeepsOnlyTheCurrentEntryAndItsMetadata() {
        seed(index = 1)
        val current = settings.historyStack[1]
        WebViewNavigation.clearHistory(settings)
        WebViewNavigation.goBack(loaded::add, settings)
        WebViewNavigation.goForward(loaded::add, settings)

        assertEquals(listOf(current), settings.historyStack)
        assertHistory(0, second)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun clearingDuringATraversalCannotBringBackTheDiscardedEntries() {
        seed(index = 1)
        val target = settings.historyStack[1]
        WebViewNavigation.clearHistory(settings)
        append(next, second, traversalEntryId = target.id)

        assertHistory(0, next)
        assertEquals(target.copy(url = next), settings.historyStack.single())
    }

    @Test
    fun aCompleteHistoryResetInvalidatesTheTraversalTarget() {
        seed()
        val staleEntryId = settings.historyStack[1].id
        WebViewNavigation.goBack(loaded::add, settings)
        settings.clearHistory()
        append(next, second, traversalEntryId = staleEntryId)

        assertHistory(0, next)
    }

    @Test
    fun clearingAnEmptyHistoryRepairsTheCursor() {
        settings.historyIndex = 100
        WebViewNavigation.clearHistory(settings)
        assertHistory(-1)
    }

    @Test
    fun invalidTraversalIndicesNeverInvokeTheLoader() {
        for (index in listOf(-1, -2, Int.MIN_VALUE, 3, Int.MAX_VALUE)) {
            seed(index)
            WebViewNavigation.goBack(loaded::add, settings)
            WebViewNavigation.goForward(loaded::add, settings)
        }
        seed()
        WebViewNavigation.navigateToIndex(loaded::add, settings, -1)
        WebViewNavigation.navigateToIndex(loaded::add, settings, 3)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun appendingWithACorruptCursorCannotCrashOrWriteAnInvalidIndex() {
        for (index in listOf(Int.MIN_VALUE, -2, 3, Int.MAX_VALUE)) {
            seed(index)
            append(next)
            assertEquals(next, settings.currentUrl)
            assertEquals(settings.historyStack.lastIndex, settings.historyIndex)
        }
        settings.clearHistory()
        settings.historyIndex = Int.MAX_VALUE
        append(next)
        assertHistory(0, next)
    }

    @Test
    fun homeCanKeepHistoryAndAvoidReloadingTheCurrentHomePage() {
        seed()
        WebViewNavigation.goHome(loaded::add, settings, userSettings)
        assertEquals(listOf(first), loaded)
        assertHistory(2, first, second, third)

        settings.historyIndex = 0
        loaded.clear()
        WebViewNavigation.goHome(loaded::add, settings, userSettings)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun homeClearsHistoryBeforeLoadingEvenWhenAlreadyAtHome() {
        seed(index = 0)
        userSettings.clearHistoryOnHome = true
        WebViewNavigation.goHome({ url ->
            assertHistory(-1)
            loaded.add(url)
            append(url)
        }, settings, userSettings)

        assertEquals(listOf(first), loaded)
        assertHistory(0, first)
    }

    @Test
    fun refreshHonoursTheSettingAndKeepsTheCurrentHistoryEntry() {
        seed(index = 1)
        val entries = settings.historyStack
        userSettings.allowRefresh = false
        WebViewNavigation.refresh(loaded::add, settings, userSettings)
        assertTrue(loaded.isEmpty())

        userSettings.allowRefresh = true
        WebViewNavigation.refresh({ url -> loaded.add(url); append(url) }, settings, userSettings)
        assertEquals(listOf(second), loaded)
        assertEquals(entries, settings.historyStack)
        assertEquals(1, settings.historyIndex)
    }

    private fun seed(index: Int = 2) {
        settings.historyStack = listOf(
            HistoryEntry("first-entry", first, 100),
            HistoryEntry("second-entry", second, 200),
            HistoryEntry("third-entry", third, 300),
        )
        settings.historyIndex = index
        settings.urlBeforeNavigation = ""
    }

    private fun append(
        url: String,
        originalUrl: String? = null,
        replace: Boolean = true,
        traversalEntryId: String? = null,
    ) = WebViewNavigation.appendWebviewHistory(settings, url, originalUrl, replace, traversalEntryId)

    private fun commit(
        session: WebViewNavigationSession,
        url: String,
        originalUrl: String?,
        nativeHistoryIndex: Int? = null,
    ) {
        if (!session.acceptsHistory(url, originalUrl, currentUrl = url)) return
        session.historyCommitted(url, originalUrl, nativeHistoryIndex)
        append(
            url, originalUrl,
            replace = !session.isSameDocumentNavigation(),
            traversalEntryId = session.traversalEntryId(),
        )
    }

    private fun assertHistory(index: Int, vararg urls: String) {
        assertEquals(urls.toList(), settings.historyStack.map { it.url })
        assertEquals(index, settings.historyIndex)
        assertEquals(urls.getOrNull(index) ?: "", settings.currentUrl)
    }
}
