package uk.nktnet.webviewkiosk.activation

import android.os.Build

internal data class ActivationResult(
    val success: Boolean,
    val output: String,
    val diagnostics: String = "",
)

internal data class CommandResult(val code: Int, val output: String) {
    fun requireSuccess(): String {
        check(code == 0) { output.ifBlank { "Command failed (exit $code)" } }
        return output.trim()
    }
}

/**
 * Runs in Shizuku's shell/root process, independently of the screen lifecycle.
 * Setup-state workaround attribution and compatibility discussion:
 * - https://xdaforums.com/t/how-i-got-device-owner-without-factory-reset.4745834/#post-90768446
 * - https://github.com/BinTianqi/OwnDroid/issues/371
 * - https://github.com/iamr0s/Dhizuku/issues/347
 *
 * Before Android 12, forceUpdateUserSetupComplete is absent or does nothing on
 * production builds. Only the setup-state workaround needs Android 12+.
 */
internal class OwnerActivation(
    private val sdkInt: Int,
    private val adminComponent: String,
    private val command: (List<String>) -> CommandResult,
    private val refreshSetupState: (Int) -> Unit,
) {
    fun activate(profileOwner: Boolean, userId: Int): ActivationResult {
        val messages = mutableListOf<String>()
        val diagnostics = mutableListOf<String>()
        var restoreSetup = false
        var success = false
        fun settings(vararg args: String) =
            command(listOf("settings", "--user", userId.toString()) + args)
        fun refresh(restoring: Boolean = false) {
            fun failed(error: Throwable) {
                diagnostics +=
                    "Device policy setup refresh:\n${error.stackTraceToString()}"
                messages +=
                    if (restoring) {
                        "Android could not refresh the restored setup state. Restart " +
                            "the device before using its policies."
                    } else {
                        "The optional account workaround is unavailable on this device."
                    }
            }
            try {
                refreshSetupState(userId)
            } catch (e: Exception) {
                failed(e)
            } catch (e: LinkageError) {
                // Hidden methods can be absent or have a different OEM ABI.
                failed(e)
            }
        }

        try {
            checkActivation(sdkInt >= Build.VERSION_CODES.M) {
                "Shizuku activation requires Android 6 or later."
            }
            checkActivation(userId >= 0) {
                "Android could not identify the current user. Open the app in the " +
                    "user you want to manage and try again."
            }
            if (!profileOwner) {
                val designatedFullUserId =
                    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        command(listOf("cmd", "user", "get-main-user"))
                            .requireSuccess()
                            .toIntOrNull()
                            ?.takeIf { it >= 0 }
                            ?: throw ActivationException(
                                "Android could not identify the designated full user. " +
                                    "Open the app in that user or use the ADB activation guide."
                            )
                    } else {
                        // Before Android 14, user 0 is the supported full-user
                        // target.
                        // Do not guess on older headless-system devices.
                        checkActivation(
                            command(
                                    listOf(
                                        "getprop",
                                        "ro.fw.mu.headless_system_user",
                                    )
                                )
                                .requireSuccess()
                                .lowercase() !in setOf("true", "1", "yes", "y", "on")
                        ) {
                            "Automatic Device Owner activation cannot identify this " +
                                "device's designated full user. Use the ADB activation " +
                                "guide instead."
                        }
                        0
                    }
                checkActivation(userId == designatedFullUserId) {
                    "Device Owner activation is only available in the designated " +
                        "full user (ID $designatedFullUserId). " +
                        "Open the app in that user and try again."
                }
            }

            if (sdkInt >= Build.VERSION_CODES.S) {
                val original =
                    settings("get", "secure", "user_setup_complete").requireSuccess()
                checkActivation(original in setOf("0", "1", "null")) {
                    "Android could not read its setup state safely. Use the ADB " +
                        "activation guide instead."
                }
                if (original == "1") {
                    // Restore even if a command throws after changing the setting.
                    restoreSetup = true
                    settings("put", "secure", "user_setup_complete", "0")
                        .requireSuccess()
                    refresh()
                }
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
            diagnostics += result.output
            messages +=
                if (success) {
                    if (profileOwner) {
                        "Profile Owner is now active in user $userId."
                    } else {
                        "Device Owner is now active."
                    }
                } else {
                    activationFailure(IllegalStateException(result.output)).output
                }
        } catch (e: Exception) {
            val failure = activationFailure(e)
            messages += failure.output
            diagnostics += failure.diagnostics
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
                    diagnostics +=
                        "Restore setup for user $userId:\n${e.stackTraceToString()}"
                    messages += "Android could not restore the original setup state."
                } finally {
                    refresh(restoring = true)
                }
            }
        }
        return ActivationResult(
            success,
            messages.distinct().joinToString("\n\n"),
            diagnostics.joinToString("\n\n").take(32_768),
        )
    }
}
