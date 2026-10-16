package uk.nktnet.webviewkiosk.utils.webview

/** State owned by one WebView, including loads that have not committed yet. */
internal class WebViewNavigationSession(private val isOwnerActive: () -> Boolean) {
    private data class Load(
        var requestedUrl: String,
        var latestUrl: String,
        var historyEntryId: String?,
        var awaitingRedirectCommit: Boolean = false,
        var hasCommittedDifferentUrl: Boolean = false,
    )

    private var load: Load? = null
    private var disposed = false
    private var committedHistoryIndex: Int? = null
    private var committedOriginalUrl: String? = null
    private var sameDocumentNavigation = false

    fun isActive(): Boolean = !disposed && isOwnerActive()

    fun beginLoad(
        url: String,
        currentEntryId: String?,
        currentEntryUrl: String?,
        historyUrl: String = url,
    ) {
        if (!isActive()) {
            return
        }
        committedHistoryIndex = null
        committedOriginalUrl = null
        sameDocumentNavigation = false
        load =
            Load(
                requestedUrl = url,
                latestUrl = url,
                historyEntryId =
                    currentEntryId.takeIf {
                        sameNavigationUrl(historyUrl, currentEntryUrl)
                    },
            )
    }

    fun mainFrameNavigation(url: String, isRedirect: Boolean?) {
        if (!isActive()) {
            return
        }
        val pending = load
        // Before API 24 the override callback cannot distinguish a redirect.
        // Keep the pending load until its history/completion callback resolves it.
        if (pending != null && isRedirect != false) {
            pending.latestUrl = url
            pending.awaitingRedirectCommit = true
        } else {
            beginLoad(url, null, null)
        }
    }

    fun acceptsPageStart(url: String?): Boolean {
        if (!isActive() || url.isNullOrEmpty()) {
            return false
        }
        val pending = load ?: return true
        return sameNavigationUrl(url, pending.latestUrl) ||
            (!pending.hasCommittedDifferentUrl &&
                sameNavigationUrl(url, pending.requestedUrl))
    }

    fun pageStarted(url: String) {
        // POST loads can start without a shouldOverrideUrlLoading callback.
        if (load == null) {
            beginLoad(url, null, null)
        }
    }

    fun acceptsHistory(
        url: String?,
        originalUrl: String?,
        currentUrl: String?,
    ): Boolean {
        if (!isActive() || url.isNullOrEmpty()) {
            return false
        }
        val pending = load ?: return sameNavigationUrl(url, currentUrl)
        return sameNavigationUrl(url, pending.latestUrl) ||
            (!pending.awaitingRedirectCommit
            // After a redirect commits, a delayed callback for the original
            // request must not rewind the active navigation to that document.
            &&
                !(pending.hasCommittedDifferentUrl &&
                    sameNavigationUrl(url, pending.requestedUrl)) &&
                sameNavigationUrl(originalUrl, pending.requestedUrl) &&
                sameNavigationUrl(url, currentUrl))
    }

    fun historyCommitted(
        url: String,
        originalUrl: String?,
        nativeHistoryIndex: Int? = null,
    ) {
        if (
            nativeHistoryIndex != null &&
                committedHistoryIndex != null &&
                nativeHistoryIndex != committedHistoryIndex &&
                sameNavigationUrl(originalUrl, committedOriginalUrl)
        ) {
            // pushState/fragment navigation creates a native history entry in the
            // same document; it must not borrow a still-loading traversal's entry.
            sameDocumentNavigation = true
            load?.historyEntryId = null
        }
        if (nativeHistoryIndex != null) {
            committedHistoryIndex = nativeHistoryIndex
            committedOriginalUrl = originalUrl
        }
        val pending = load ?: return
        if (
            !originalUrl.isNullOrEmpty() &&
                !sameNavigationUrl(originalUrl, pending.requestedUrl) &&
                !sameNavigationUrl(url, pending.requestedUrl)
        ) {
            // A legacy override can be a new user navigation rather than a redirect.
            pending.historyEntryId = null
            pending.requestedUrl = originalUrl
            sameDocumentNavigation = false
        }
        pending.latestUrl = url
        pending.hasCommittedDifferentUrl =
            !sameNavigationUrl(url, pending.requestedUrl)
        pending.awaitingRedirectCommit = false
    }

    fun traversalEntryId(): String? = load?.historyEntryId

    fun isSameDocumentNavigation(): Boolean = sameDocumentNavigation

    /** A completion token belongs to the exact load active at callback entry. */
    fun acceptedCompletionToken(url: String?, currentUrl: String?): Any? {
        // An older onPageFinished can be reentrant with UI callbacks that load
        // another page (including a reload of the exact same URL). Comparing
        // URLs alone after those callbacks cannot identify the owning load.
        val pending = load ?: return null
        if (!isActive() || url.isNullOrEmpty()) {
            return null
        }
        val matches =
            (sameNavigationUrl(url, currentUrl) &&
                sameNavigationUrl(url, pending.latestUrl)) ||
                (sameDocumentNavigation &&
                    sameNavigationUrl(url, pending.requestedUrl) &&
                    sameNavigationUrl(currentUrl, pending.latestUrl))
        return pending.takeIf { matches }
    }

    fun acceptsCompletion(url: String?, currentUrl: String?): Boolean =
        acceptedCompletionToken(url, currentUrl) != null

    fun acceptsError(
        url: String?,
        currentUrl: String?,
        isMainFrame: Boolean,
    ): Boolean =
        isMainFrame &&
            isActive() &&
            !url.isNullOrEmpty() &&
            sameNavigationUrl(url, load?.latestUrl ?: currentUrl)

    /** Finish only the load accepted before any potentially reentrant callbacks. */
    fun finishLoad(expectedLoad: Any): Boolean {
        if (!isActive() || load !== expectedLoad) {
            return false
        }
        load = null
        return true
    }

    // Used by the non-reentrant state-machine fixtures.
    fun finishLoad() {
        load = null
    }

    /**
     * Invalidate callbacks before running cleanup, which can itself invoke
     * callbacks.
     */
    fun dispose(): Boolean {
        if (disposed) {
            return false
        }
        disposed = true
        load = null
        return true
    }
}
