package uk.nktnet.webviewkiosk.managers

/** Session intervals use a monotonic clock that includes time spent asleep. */
internal class AuthenticationSession(private val now: () -> Long) {
    private var lastAuthenticatedAt: Long? = null
    private var externalActivityStartedAt: Long? = null
    private var externalActivityWindowMs = 0L
    private var externalActivityRoundTrip = false

    fun isValid(): Boolean = isValidAt(now())

    private fun isValidAt(time: Long): Boolean {
        fun withinWindow(start: Long?, duration: Long): Boolean =
            start != null && time >= start && time - start < duration

        return withinWindow(lastAuthenticatedAt, AUTH_TIMEOUT_MS)
            || withinWindow(externalActivityStartedAt, externalActivityWindowMs)
    }

    fun authenticate() {
        lastAuthenticatedAt = now()
        clearExternalActivityWindow()
    }

    fun refreshIfValid(): Boolean {
        val time = now()
        val valid = isValidAt(time)
        if (valid) {
            lastAuthenticatedAt = time
        }
        clearExternalActivityWindow()
        return valid
    }

    fun reset(preserveExternalActivitySession: Boolean = false) {
        val preserveWindow = preserveExternalActivitySession
            && externalActivityRoundTrip
            && isValid()
        lastAuthenticatedAt = null
        if (!preserveWindow) {
            clearExternalActivityWindow()
        }
        externalActivityRoundTrip = false
    }

    fun preserveForExternalActivity(durationMs: Long) {
        val time = now()
        if (durationMs <= 0 || !isValidAt(time)) {
            return
        }
        externalActivityStartedAt = time
        externalActivityWindowMs = durationMs
        externalActivityRoundTrip = true
    }

    private fun clearExternalActivityWindow() {
        externalActivityStartedAt = null
        externalActivityWindowMs = 0L
        externalActivityRoundTrip = false
    }

    private companion object {
        const val AUTH_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
