package uk.nktnet.webviewkiosk.activation

/** Only messages written by this app may be shown verbatim to the user. */
internal class ActivationException(message: String) : IllegalStateException(message)

internal fun checkActivation(condition: Boolean, message: () -> String) {
    if (!condition) {
        throw ActivationException(message())
    }
}

/** Keep the summary readable and preserve stacktraces for expandable details. */
internal fun activationFailure(
    error: Throwable,
    fallback: String =
        "Could not activate ownership. Keep Shizuku running and try again. " +
            "If this continues, use ADB or factory reset and QR setup.",
): ActivationResult {
    val diagnostics = error.stackTraceToString().take(32_768)
    // Classify exception messages, not incidental method names in stack frames.
    val reason =
        generateSequence(error) { it.cause }
            .take(6)
            .joinToString("\n") {
                "${it.javaClass.simpleName}: ${it.message.orEmpty()}"
            }
            .take(32_768)
            .lowercase()
    val message =
        when {
            error is ActivationException -> error.message ?: fallback
            "timed out" in reason || "timeoutexception" in reason ->
                "Activation timed out. Check that Shizuku is running, then try again."
            "deadobjectexception" in reason || "disconnected" in reason ->
                "Shizuku disconnected. Open Shizuku, restart its service, then try again."
            "not running" in reason || "binder haven't been received" in reason ->
                "Shizuku is not running. Open Shizuku, start its service, then try again."
            "owner is already" in reason ||
                "owner already" in reason ||
                "already has a device owner" in reason ||
                "already has a profile owner" in reason ||
                "already managed" in reason ->
                "An owner is already configured. Check Device Policies and the existing " +
                    "management app before trying again."
            "no device owner" in reason ||
                "device owner is not set" in reason ||
                "device owner does not exist" in reason ||
                "device owner doesn't exist" in reason ||
                "deviceowner does not exist" in reason ||
                "owner not found" in reason ->
                "No device owner is configured. Activate Device Owner first, or choose " +
                    "Profile Owner for the main user."
            "account" in reason ->
                "Android blocked activation because accounts are configured. Remove " +
                    "conflicting accounts, or back up the device and use factory reset " +
                    "and QR setup."
            "several users" in reason ||
                "multiple users" in reason ||
                "additional users" in reason ->
                "Android blocked activation because other users or profiles exist. " +
                    "Review them in Android Settings before trying again."
            "unknown admin" in reason ||
                "not a valid admin" in reason ||
                "invalid component" in reason ||
                "bad component" in reason ->
                "Android could not find this app's administrator. Reinstall the app " +
                    "in the main user, then try again."
            "already set-up" in reason || "already set up" in reason ->
                "Android blocked activation after device setup. Try the Shizuku method " +
                    "on Android 12+, or back up the device and use factory reset and QR setup."
            "securityexception" in reason ||
                "permission" in reason ||
                "not allowed" in reason ->
                "Android denied permission to activate ownership. Allow this app in " +
                    "Shizuku, check device restrictions, then try again."
            else -> fallback
        }
    return ActivationResult(false, message, diagnostics)
}
