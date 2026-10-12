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
        if (!isActive()) return
        committedHistoryIndex = null
        committedOriginalUrl = null
        sameDocumentNavigation = false
        load = Load(
            requestedUrl = url,
            latestUrl = url,
            historyEntryId = currentEntryId.takeIf {
                sameNavigationUrl(historyUrl, currentEntryUrl)
            },
        )
    }

    fun mainFrameNavigation(url: String, isRedirect: Boolean?) {
        if (!isActive()) return
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
        if (!isActive() || url.isNullOrEmpty()) return false
        val pending = load ?: return true
        return sameNavigationUrl(url, pending.latestUrl) || (
            !pending.hasCommittedDifferentUrl && sameNavigationUrl(url, pending.requestedUrl)
        )
    }

    fun pageStarted(url: String) {
        // POST loads can start without a shouldOverrideUrlLoading callback.
        if (load == null) beginLoad(url, null, null)
    }

    fun acceptsHistory(url: String?, originalUrl: String?, currentUrl: String?): Boolean {
        if (!isActive() || url.isNullOrEmpty()) return false
        val pending = load ?: return sameNavigationUrl(url, currentUrl)
        return sameNavigationUrl(url, pending.latestUrl) || (
            !pending.awaitingRedirectCommit
            // After a redirect commits, a delayed callback for the original
            // request must not rewind the active navigation to that document.
            && !(pending.hasCommittedDifferentUrl && sameNavigationUrl(url, pending.requestedUrl))
            && sameNavigationUrl(originalUrl, pending.requestedUrl)
            && sameNavigationUrl(url, currentUrl)
        )
    }

    fun historyCommitted(url: String, originalUrl: String?, nativeHistoryIndex: Int? = null) {
        if (
            nativeHistoryIndex != null && committedHistoryIndex != null
            && nativeHistoryIndex != committedHistoryIndex
            && sameNavigationUrl(originalUrl, committedOriginalUrl)
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
            !originalUrl.isNullOrEmpty()
            && !sameNavigationUrl(originalUrl, pending.requestedUrl)
            && !sameNavigationUrl(url, pending.requestedUrl)
        ) {
            // A legacy override can be a new user navigation rather than a redirect.
            pending.historyEntryId = null
            pending.requestedUrl = originalUrl
            sameDocumentNavigation = false
        }
        pending.latestUrl = url
        pending.hasCommittedDifferentUrl = !sameNavigationUrl(url, pending.requestedUrl)
        pending.awaitingRedirectCommit = false
    }

    fun traversalEntryId(): String? = load?.historyEntryId

    fun isSameDocumentNavigation(): Boolean = sameDocumentNavigation

    fun acceptsCompletion(url: String?, currentUrl: String?): Boolean {
        // A completed load has no owner. WebView can deliver onPageFinished
        // more than once (even at 100% progress); do not repeat injected scripts,
        // clear another load's state, or consume a newer navigation's history.
        val pending = load ?: return false
        return isActive() && !url.isNullOrEmpty() && (
            (
                sameNavigationUrl(url, currentUrl)
                && sameNavigationUrl(url, pending.latestUrl)
            )
            || (
                sameDocumentNavigation && sameNavigationUrl(url, pending.requestedUrl)
                && sameNavigationUrl(currentUrl, pending.latestUrl)
            )
        )
    }

    fun acceptsError(url: String?, currentUrl: String?, isMainFrame: Boolean): Boolean =
        isMainFrame && isActive() && !url.isNullOrEmpty()
            && sameNavigationUrl(url, load?.latestUrl ?: currentUrl)

    fun finishLoad() {
        load = null
    }

    /** Invalidate callbacks before running cleanup, which can itself invoke callbacks. */
    fun dispose(): Boolean {
        if (disposed) return false
        disposed = true
        load = null
        return true
    }
}
