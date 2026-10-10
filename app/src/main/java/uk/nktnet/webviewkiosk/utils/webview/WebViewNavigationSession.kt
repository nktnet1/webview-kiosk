package uk.nktnet.webviewkiosk.utils.webview

/** State owned by one WebView, including loads that have not committed yet. */
internal class WebViewNavigationSession(private val isOwnerActive: () -> Boolean) {
    private data class Load(
        var requestedUrl: String,
        var latestUrl: String,
        var historyEntryId: String?,
        var awaitingRedirectCommit: Boolean = false,
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
                sameUrl(historyUrl, currentEntryUrl)
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

    fun acceptsPageStart(url: String?): Boolean =
        isActive() && !url.isNullOrEmpty() && (
            load == null || sameUrl(url, load?.latestUrl) || sameUrl(url, load?.requestedUrl)
        )

    fun pageStarted(url: String) {
        // POST loads can start without a shouldOverrideUrlLoading callback.
        if (load == null) beginLoad(url, null, null)
    }

    fun acceptsHistory(url: String?, originalUrl: String?, currentUrl: String?): Boolean {
        if (!isActive() || url.isNullOrEmpty()) return false
        val pending = load ?: return sameUrl(url, currentUrl)
        return sameUrl(url, pending.latestUrl) || (
            !pending.awaitingRedirectCommit
            && sameUrl(originalUrl, pending.requestedUrl) && sameUrl(url, currentUrl)
        )
    }

    fun historyCommitted(url: String, originalUrl: String?, nativeHistoryIndex: Int? = null) {
        if (
            nativeHistoryIndex != null && committedHistoryIndex != null
            && nativeHistoryIndex != committedHistoryIndex
            && sameUrl(originalUrl, committedOriginalUrl)
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
            && !sameUrl(originalUrl, pending.requestedUrl)
            && !sameUrl(url, pending.requestedUrl)
        ) {
            // A legacy override can be a new user navigation rather than a redirect.
            pending.historyEntryId = null
            pending.requestedUrl = originalUrl
            sameDocumentNavigation = false
        }
        pending.latestUrl = url
        pending.awaitingRedirectCommit = false
    }

    fun traversalEntryId(): String? = load?.historyEntryId

    fun isSameDocumentNavigation(): Boolean = sameDocumentNavigation

    fun acceptsCompletion(url: String?, currentUrl: String?): Boolean =
        isActive() && !url.isNullOrEmpty() && (
            (sameUrl(url, currentUrl) && (load == null || sameUrl(url, load?.latestUrl)))
            || (
                sameDocumentNavigation && sameUrl(url, load?.requestedUrl)
                && sameUrl(currentUrl, load?.latestUrl)
            )
        )

    fun acceptsError(url: String?, currentUrl: String?, isMainFrame: Boolean): Boolean =
        isMainFrame && isActive() && !url.isNullOrEmpty()
            && sameUrl(url, load?.latestUrl ?: currentUrl)

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

    private fun sameUrl(first: String?, second: String?): Boolean =
        first != null && second != null && first.trimEnd('/') == second.trimEnd('/')
}
