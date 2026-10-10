package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewNavigationSessionTest {
    private val first = "https://example.com/first"
    private val second = "https://example.com/second"
    private val third = "https://example.com/third"

    @Test
    fun aRequestedLoadCanCommitBeforeWebViewChangesItsCurrentUrl() {
        val session = session()
        session.beginLoad(second, null, first)

        assertTrue(session.acceptsPageStart(second))
        assertTrue(session.acceptsHistory(second, first, first))
        assertFalse(session.acceptsCompletion(second, first))
        session.historyCommitted(second, second)
        assertTrue(session.acceptsCompletion(second, second))
    }

    @Test
    fun aNewLoadRejectsCallbacksFromThePreviousPageEvenBeforeItCommits() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.beginLoad(second, "first-entry", first)

        assertFalse(session.acceptsPageStart(first))
        assertFalse(session.acceptsHistory(first, first, first))
        assertFalse(session.acceptsCompletion(first, first))
        assertFalse(session.acceptsError(first, first, isMainFrame = true))
        assertNull(session.traversalEntryId())
        assertTrue(session.acceptsError(second, first, isMainFrame = true))
    }

    @Test
    fun rapidBackAndForwardLoadsKeepOnlyTheLastSelectedEntry() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.beginLoad(second, "second-entry", second)
        session.beginLoad(third, "third-entry", third)

        assertEquals("third-entry", session.traversalEntryId())
        assertFalse(session.acceptsHistory(first, first, first))
        assertFalse(session.acceptsHistory(second, second, second))
        assertTrue(session.acceptsHistory(third, third, third))
    }

    @Test
    fun redirectsWithoutAnOverrideCallbackMustBelongToTheRequestedDocument() {
        val session = session()
        session.beginLoad(first, "first-entry", first)

        assertFalse(session.acceptsHistory(second, third, second))
        assertFalse(session.acceptsHistory(second, first, third))
        assertTrue(session.acceptsHistory(second, first, second))
        session.historyCommitted(second, first)
        assertTrue(session.acceptsCompletion(second, second))
        assertEquals("first-entry", session.traversalEntryId())
        assertTrue(session.acceptsHistory(third, first, third))
        session.historyCommitted(third, first)
        assertTrue(session.acceptsCompletion(third, third))
    }

    @Test
    fun aRedirectChainRetainsTheTraversalEntryAndRejectsIntermediateFinishes() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.mainFrameNavigation(second, isRedirect = true)
        session.mainFrameNavigation(third, isRedirect = true)

        assertFalse(session.acceptsCompletion(second, second))
        assertTrue(session.acceptsPageStart(third))
        assertTrue(session.acceptsHistory(third, first, third))
        session.historyCommitted(third, first)
        assertEquals("first-entry", session.traversalEntryId())
        assertTrue(session.acceptsCompletion(third, third))
    }

    @Test
    fun aNewNativeNavigationDuringATraversalDoesNotReplaceTheSelectedEntry() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.mainFrameNavigation(second, isRedirect = false)

        assertNull(session.traversalEntryId())
        assertFalse(session.acceptsHistory(first, first, first))
        assertTrue(session.acceptsHistory(second, second, second))
    }

    @Test
    fun aPendingRedirectCannotBeOverwrittenByThePreviousDocumentsLateHistoryCallback() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.mainFrameNavigation(second, isRedirect = true)
        session.historyCommitted(second, first)
        session.mainFrameNavigation(third, isRedirect = true)

        assertFalse(session.acceptsHistory(second, first, second))
        assertFalse(session.acceptsCompletion(second, second))
        assertTrue(session.acceptsHistory(third, first, third))
        session.historyCommitted(third, first)
        assertTrue(session.acceptsCompletion(third, third))
        assertEquals("first-entry", session.traversalEntryId())
    }

    @Test
    fun aPageStartForTheOriginalRequestStillBelongsToItsRedirectChain() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.mainFrameNavigation(second, isRedirect = true)

        assertTrue(session.acceptsPageStart(first))
        assertTrue(session.acceptsPageStart(second))
        assertFalse(session.acceptsPageStart(third))
    }

    @Test
    fun aLegacyRedirectRetainsTheSelectedEntryUntilCompletion() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.mainFrameNavigation(second, isRedirect = null)
        session.historyCommitted(second, first)

        assertEquals("first-entry", session.traversalEntryId())
        assertTrue(session.acceptsCompletion(second, second))
        session.finishLoad()
        assertNull(session.traversalEntryId())
    }

    @Test
    fun aLegacyNewDocumentCannotBorrowThePendingTraversal() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.mainFrameNavigation(second, isRedirect = null)
        session.historyCommitted(second, second)

        assertNull(session.traversalEntryId())
        assertTrue(session.acceptsCompletion(second, second))
        assertTrue(session.acceptsHistory(third, second, third))
        session.historyCommitted(third, second)
        assertTrue(session.acceptsCompletion(third, third))
    }

    @Test
    fun duplicateHistoryCallbacksDoNotConsumeTheTraversal() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        repeat(3) {
            assertTrue(session.acceptsHistory(first, first, first))
            session.historyCommitted(first, first)
            assertEquals("first-entry", session.traversalEntryId())
        }
        session.mainFrameNavigation(second, isRedirect = true)
        session.historyCommitted(second, first)
        assertEquals("first-entry", session.traversalEntryId())
    }

    @Test
    fun completionReleasesTraversalSoSpaNavigationCanAppendHistory() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.finishLoad()

        assertNull(session.traversalEntryId())
        assertTrue(session.acceptsHistory(second, first, second))
        assertTrue(session.acceptsHistory(third, first, third))
    }

    @Test
    fun aSpaPushBeforePageFinishCannotBorrowTheTraversalEntry() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.historyCommitted(first, first, nativeHistoryIndex = 4)
        session.historyCommitted(second, first, nativeHistoryIndex = 5)

        assertNull(session.traversalEntryId())
        assertTrue(session.isSameDocumentNavigation())
        // onPageFinished can name the document's original URL after pushState.
        assertTrue(session.acceptsCompletion(first, second))
        assertFalse(session.acceptsCompletion(third, second))
    }

    @Test
    fun spaPushesAfterCompletionRemainSeparateUntilANewDocumentStarts() {
        val session = session()
        session.beginLoad(first, null, null)
        session.historyCommitted(first, first, nativeHistoryIndex = 4)
        session.finishLoad()
        session.historyCommitted(second, first, nativeHistoryIndex = 5)
        session.historyCommitted(third, first, nativeHistoryIndex = 6)

        assertTrue(session.isSameDocumentNavigation())
        session.mainFrameNavigation(second, isRedirect = false)
        session.historyCommitted(second, second, nativeHistoryIndex = 7)
        assertFalse(session.isSameDocumentNavigation())

        val legacy = session()
        legacy.beginLoad(first, "first-entry", first)
        legacy.historyCommitted(first, first, nativeHistoryIndex = 4)
        legacy.historyCommitted(second, first, nativeHistoryIndex = 5)
        legacy.mainFrameNavigation(third, isRedirect = null)
        legacy.historyCommitted(third, third, nativeHistoryIndex = 6)
        assertFalse(legacy.isSameDocumentNavigation())
        assertTrue(legacy.acceptsHistory(second, third, second))
    }

    @Test
    fun aRedirectWithinOneNativeHistoryEntryDoesNotPermitAnOldFinish() {
        val session = session()
        session.beginLoad(first, "first-entry", first)
        session.historyCommitted(first, first, nativeHistoryIndex = 4)
        session.mainFrameNavigation(second, isRedirect = true)
        session.historyCommitted(second, first, nativeHistoryIndex = 4)

        assertFalse(session.isSameDocumentNavigation())
        assertEquals("first-entry", session.traversalEntryId())
        assertFalse(session.acceptsCompletion(first, second))
        assertTrue(session.acceptsCompletion(second, second))
    }

    @Test
    fun aLateHistoryCallbackAfterCompletionCannotRestoreThePreviousPage() {
        val session = session()
        session.beginLoad(second, null, first)
        session.historyCommitted(second, second)
        session.finishLoad()

        assertFalse(session.acceptsHistory(first, first, second))
        assertTrue(session.acceptsHistory(second, second, second))
        assertTrue(session.acceptsHistory(third, second, third))
    }

    @Test
    fun aPostLoadWithoutAnOverrideCallbackCanReportAnErrorBeforeCommit() {
        val session = session()
        assertTrue(session.acceptsPageStart(second))
        session.pageStarted(second)

        assertTrue(session.acceptsError(second, first, isMainFrame = true))
        assertFalse(session.acceptsError(first, first, isMainFrame = true))
        assertFalse(session.acceptsHistory(first, first, first))
        assertTrue(session.acceptsHistory(second, second, second))
        assertNull(session.traversalEntryId())
    }

    @Test
    fun loadingAnotherUrlDoesNotCreateATraversal() {
        val session = session()
        session.beginLoad(second, "first-entry", first)
        assertNull(session.traversalEntryId())
    }

    @Test
    fun aPdfViewerCanSelectTheHistoryEntryForItsSourceUrl() {
        val session = session()
        val viewer = "https://viewer.example/index.html?file=source.pdf"
        session.beginLoad(viewer, "pdf-entry", first, historyUrl = first)

        assertEquals("pdf-entry", session.traversalEntryId())
        assertTrue(session.acceptsHistory(viewer, viewer, viewer))
        assertTrue(session.acceptsCompletion(viewer, viewer))
    }

    @Test
    fun completionFromAnotherCurrentDocumentIsIgnored() {
        val session = session()
        assertFalse(session.acceptsCompletion(first, second))
        session.beginLoad(second, null, null)
        assertFalse(session.acceptsCompletion(first, first))
        assertTrue(session.acceptsCompletion(second, second))
    }

    @Test
    fun subresourceErrorsCannotChangeMainFrameStateOrRequestPermission() {
        val session = session()
        session.beginLoad(first, null, null)

        assertFalse(session.acceptsError(first, first, isMainFrame = false))
        assertFalse(session.acceptsError(second, first, isMainFrame = false))
        assertTrue(session.acceptsError(first, second, isMainFrame = true))
    }

    @Test
    fun errorsAfterCompletionMustMatchTheCurrentPage() {
        val session = session()
        session.beginLoad(first, null, null)
        session.finishLoad()

        assertFalse(session.acceptsError(first, second, isMainFrame = true))
        assertTrue(session.acceptsError(second, second, isMainFrame = true))
    }

    @Test
    fun anOutgoingEntryCannotAffectTheReplacementEvenWhenBothRoutesMatch() {
        val oldOwner = Any()
        val newOwner = Any()
        var currentOwner = oldOwner
        val outgoing = WebViewNavigationSession { currentOwner === oldOwner }
        outgoing.beginLoad(first, "old-entry", first)
        currentOwner = newOwner
        val incoming = WebViewNavigationSession { currentOwner === newOwner }
        incoming.beginLoad(second, "new-entry", second)

        assertFalse(outgoing.isActive())
        assertFalse(outgoing.acceptsPageStart(first))
        assertFalse(outgoing.acceptsHistory(first, first, first))
        assertFalse(outgoing.acceptsCompletion(first, first))
        assertFalse(outgoing.acceptsError(first, first, isMainFrame = true))
        outgoing.dispose()
        assertTrue(incoming.isActive())
        assertEquals("new-entry", incoming.traversalEntryId())
        assertTrue(incoming.acceptsHistory(second, second, second))
    }

    @Test
    fun rendererRecreationInvalidatesThePreviousViewWithinTheSameEntry() {
        var currentGeneration = 0
        val outgoing = WebViewNavigationSession { currentGeneration == 0 }
        outgoing.beginLoad(first, "entry", first)
        currentGeneration++
        val incoming = WebViewNavigationSession { currentGeneration == 1 }

        assertFalse(outgoing.isActive())
        assertFalse(outgoing.acceptsCompletion(first, first))
        assertFalse(outgoing.acceptsError(first, first, isMainFrame = true))
        assertTrue(incoming.isActive())
    }

    @Test
    fun twoLiveSessionsDoNotShareTraversalOrDisposalState() {
        val firstSession = session()
        val secondSession = session()
        firstSession.beginLoad(first, "first-entry", first)
        secondSession.beginLoad(second, "second-entry", second)

        firstSession.historyCommitted(first, first)
        firstSession.finishLoad()
        firstSession.dispose()

        assertTrue(secondSession.isActive())
        assertEquals("second-entry", secondSession.traversalEntryId())
        assertTrue(secondSession.acceptsCompletion(second, second))
    }

    @Test
    fun disposalIsIdempotentAndRejectsReentrantOrLateCallbacks() {
        val session = session()
        session.beginLoad(first, "entry", first)

        assertTrue(session.dispose())
        assertFalse(session.dispose())
        session.beginLoad(second, "entry", second)
        session.mainFrameNavigation(third, isRedirect = true)
        assertNull(session.traversalEntryId())
        assertFalse(session.isActive())
        assertFalse(session.acceptsPageStart(second))
        assertFalse(session.acceptsHistory(second, second, second))
        assertFalse(session.acceptsCompletion(second, second))
        assertFalse(session.acceptsError(second, second, isMainFrame = true))
    }

    @Test
    fun nullAndEmptyCallbackUrlsCannotChangeNavigationState() {
        val session = session()
        for (url in listOf(null, "")) {
            assertFalse(session.acceptsPageStart(url))
            assertFalse(session.acceptsHistory(url, first, first))
            assertFalse(session.acceptsCompletion(url, url))
            assertFalse(session.acceptsError(url, url, isMainFrame = true))
        }
    }

    private fun session() = WebViewNavigationSession { true }
}
