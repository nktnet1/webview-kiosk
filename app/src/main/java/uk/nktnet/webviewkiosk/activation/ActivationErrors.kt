package uk.nktnet.webviewkiosk.activation

/** Explicit activation precondition failure. */
internal class ActivationException(message: String) : IllegalStateException(message)

internal fun checkActivation(condition: Boolean, message: () -> String) {
    if (!condition) {
        throw ActivationException(message())
    }
}

/** Keep the summary short and preserve the original exception diagnostics. */
internal fun activationFailure(error: Throwable): ActivationResult {
    val message =
        error.message
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull(String::isNotEmpty)
            ?.take(300) ?: "Could not activate ownership."
    return ActivationResult(false, message, error.stackTraceToString().take(32_768))
}
