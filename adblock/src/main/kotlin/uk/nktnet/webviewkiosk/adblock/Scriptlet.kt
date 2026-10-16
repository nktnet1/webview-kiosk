package uk.nktnet.webviewkiosk.adblock

/** The runtime only dispatches these locally implemented operations. */
enum class ScriptletType {
    SET,
    ABORT_READ,
    ABORT_WRITE,
    PREVENT_WINDOW_OPEN,
    PREVENT_ADD_EVENT_LISTENER,
    PREVENT_SET_TIMEOUT,
    ABORT_CURRENT_SCRIPT,
    JSON_PRUNE,
    PREVENT_FETCH,
}

/** Arguments are validated during compilation and serialized as JSON data. */
@ConsistentCopyVisibility
data class ScriptletInstruction
internal constructor(
    val type: ScriptletType,
    val arguments: List<String>,
)

/**
 * Prepared off the UI thread; install before the first navigation, in page world.
 */
data class ScriptletInjection(
    val source: String,
    val allowedOrigins: Set<String>,
)
