package uk.nktnet.webviewkiosk.activation

internal data class ActivationResult(val success: Boolean, val output: String)

internal data class CommandResult(val code: Int, val output: String) {
    fun requireSuccess(): String {
        check(code == 0) { output.ifBlank { "Command failed (exit $code)" } }
        return output.trim()
    }
}

/** Runs in Shizuku's shell/root process, independently of the screen lifecycle. */
internal class OwnerActivation(
    private val sdkInt: Int,
    private val adminComponent: String,
    private val command: (List<String>) -> CommandResult,
    private val refreshSetupState: (Int) -> Unit,
) {
    fun activate(profileOwner: Boolean, userId: Int): ActivationResult {
        val messages = mutableListOf<String>()
        var restoreSetup = false
        var success = false
        fun settings(vararg args: String) =
            command(listOf("settings", "--user", userId.toString()) + args)
        fun refresh() {
            try {
                refreshSetupState(userId)
            } catch (e: Exception) {
                messages +=
                    "Could not refresh device policy setup state: ${e.message}"
            } catch (e: LinkageError) {
                // Hidden methods can be absent or have a different OEM ABI.
                messages +=
                    "Could not refresh device policy setup state: ${e.message}"
            }
        }

        try {
            val mainUser =
                if (sdkInt >= 34) {
                    command(listOf("cmd", "user", "get-main-user"))
                        .requireSuccess()
                        .toIntOrNull()
                        ?.takeIf { it >= 0 }
                        ?: error("Android did not report a main user")
                } else {
                    // Before Android 14 the full system user is the supported
                    // main user. Do not guess on older headless-system devices.
                    check(
                        command(listOf("getprop", "ro.fw.mu.headless_system_user"))
                            .requireSuccess()
                            .lowercase() !in setOf("true", "1", "yes", "y", "on")
                    ) {
                        "Cannot identify the main user on this headless device"
                    }
                    0
                }
            check(userId == mainUser) {
                "Activation is only available in the main user (ID $mainUser). " +
                    "Open the app in that user and try again."
            }

            val original =
                settings("get", "secure", "user_setup_complete").requireSuccess()
            check(original in setOf("0", "1", "null")) {
                "Unexpected user_setup_complete value: $original"
            }
            if (original == "1") {
                // Restore even if the command throws after writing the setting.
                restoreSetup = true
                settings("put", "secure", "user_setup_complete", "0")
                    .requireSuccess()
                refresh()
            }

            val result =
                command(
                    listOf(
                        "dpm",
                        if (profileOwner) {
                            "set-profile-owner"
                        } else {
                            "set-device-owner"
                        },
                        "--user",
                        userId.toString(),
                        adminComponent,
                    )
                )
            success = result.code == 0
            messages +=
                result.output.ifBlank { "DPM exited with code ${result.code}" }
        } catch (e: Exception) {
            messages += e.message ?: e.javaClass.simpleName
        } finally {
            if (restoreSetup) {
                try {
                    settings("put", "secure", "user_setup_complete", "1")
                        .requireSuccess()
                    check(
                        settings("get", "secure", "user_setup_complete")
                            .requireSuccess() == "1"
                    ) {
                        "Android did not restore the original setup flag"
                    }
                } catch (e: Exception) {
                    messages +=
                        "WARNING: could not restore user_setup_complete " +
                            "for user $userId: ${e.message}"
                } finally {
                    refresh()
                }
            }
        }
        return ActivationResult(success, messages.joinToString("\n\n"))
    }
}
