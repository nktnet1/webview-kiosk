package uk.nktnet.webviewkiosk.blocking

/**
 * Collects changes made while editing filter lists. Saved lists are validated
 * immediately, but the combined engine snapshot only needs rebuilding once. All
 * calls are made on the settings UI thread.
 */
internal class ContentBlockingEditSession(
    private val blocker: ContentBlockingService
) {
    private var pending = false

    fun markChanged() {
        pending = true
    }

    /** Returns the requested revision, or null when no rules changed. */
    fun flush(): Long? {
        if (!pending) {
            return null
        }
        pending = false
        return blocker.reload()
    }
}
