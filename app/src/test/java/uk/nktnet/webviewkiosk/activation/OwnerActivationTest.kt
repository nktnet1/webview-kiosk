package uk.nktnet.webviewkiosk.activation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerActivationTest {
    private val admin = "example.debug/example.AdminReceiver"
    private val commands = mutableListOf<List<String>>()
    private val refreshed = mutableListOf<Int>()
    private var original = "1"
    private var mainUser = "0"
    private var headless = "false"
    private var dpmResult = CommandResult(0, "Success")
    private var onCommand: (List<String>) -> Unit = {}
    private var onRefresh: () -> Unit = {}

    private fun engine(sdk: Int = 34) =
        OwnerActivation(
            sdk,
            admin,
            { args ->
                commands += args
                onCommand(args)
                when (args.first()) {
                    "cmd" -> CommandResult(0, mainUser)
                    "getprop" -> CommandResult(0, headless)
                    "settings" ->
                        CommandResult(
                            0,
                            if (args[3] == "get") {
                                original
                            } else {
                                ""
                            },
                        )
                    "dpm" -> dpmResult
                    else -> error("Unexpected command: $args")
                }
            },
            { user ->
                refreshed += user
                onRefresh()
            },
        )

    @Test
    fun deviceOwnerUsesExplicitMainUserAndRestoresSetup() {
        val result = engine().activate(false, 0)
        assertTrue(result.success)
        assertEquals(
            listOf(
                listOf("cmd", "user", "get-main-user"),
                listOf(
                    "settings",
                    "--user",
                    "0",
                    "get",
                    "secure",
                    "user_setup_complete",
                ),
                listOf(
                    "settings",
                    "--user",
                    "0",
                    "put",
                    "secure",
                    "user_setup_complete",
                    "0",
                ),
                listOf("dpm", "set-device-owner", "--user", "0", admin),
                listOf(
                    "settings",
                    "--user",
                    "0",
                    "put",
                    "secure",
                    "user_setup_complete",
                    "1",
                ),
                listOf(
                    "settings",
                    "--user",
                    "0",
                    "get",
                    "secure",
                    "user_setup_complete",
                ),
            ),
            commands,
        )
        assertEquals(listOf(0, 0), refreshed)
    }

    @Test
    fun profileOwnerUsesResolvedNonzeroMainUser() {
        mainUser = "10\n"
        val result = engine().activate(true, 10)
        assertTrue(result.success)
        assertTrue(
            listOf("dpm", "set-profile-owner", "--user", "10", admin) in commands
        )
        assertEquals(listOf(10, 10), refreshed)
        assertTrue(
            commands.filter { it.first() == "settings" }.all { it[2] == "10" }
        )
    }

    @Test
    fun secondaryUserOrWorkProfileCannotModifyAnySettings() {
        for (user in listOf(10, 11, -1)) {
            commands.clear()
            val result = engine().activate(true, user)
            assertFalse(result.success)
            assertTrue(result.output.contains("main user (ID 0)"))
            assertEquals(listOf(listOf("cmd", "user", "get-main-user")), commands)
        }
    }

    @Test
    fun unknownMainUserFailsClosedInsteadOfFallingBackToZero() {
        for (output in listOf("No main user", "-10000", "", "Error: unsupported")) {
            commands.clear()
            mainUser = output
            assertFalse(engine().activate(false, 0).success)
            assertEquals(1, commands.size)
        }
    }

    @Test
    fun android12And13UseFullSystemUserAndRefuseHeadlessDevices() {
        assertTrue(engine(31).activate(true, 0).success)
        assertFalse(commands.any { it.first() == "cmd" })
        commands.clear()
        headless = "1"
        assertFalse(engine(33).activate(true, 0).success)
        assertEquals(1, commands.size)
    }

    @Test
    fun incompleteOrMissingSetupFlagIsPreserved() {
        for (value in listOf("0", "null")) {
            commands.clear()
            original = value
            assertTrue(engine().activate(false, 0).success)
            assertFalse(commands.any { it.first() == "settings" && it[3] == "put" })
            assertTrue(refreshed.isEmpty())
        }
    }

    @Test
    fun unexpectedSetupValueAbortsWithoutWrites() {
        original = "permission denied"
        assertFalse(engine().activate(false, 0).success)
        assertFalse(commands.any { it.first() == "dpm" || "put" in it })
    }

    @Test
    fun androidRejectionIsReturnedAndSetupIsRestored() {
        dpmResult = CommandResult(1, "Error: another owner already exists")
        val result = engine().activate(true, 0)
        assertFalse(result.success)
        assertEquals("Error: another owner already exists", result.output)
        assertTrue(result.diagnostics.contains("another owner already exists"))
        assertTrue(commands.any { it.first() == "settings" && it.last() == "1" })
        assertEquals(listOf(0, 0), refreshed)
    }

    @Test
    fun commandExceptionStillRestoresSetup() {
        onCommand = {
            if (it.first() == "dpm") {
                error("Timed out")
            }
        }
        val result = engine().activate(false, 0)
        assertFalse(result.success)
        assertTrue(result.output.lowercase().contains("timed out"))
        assertTrue(commands.any { it.first() == "settings" && it.last() == "1" })
        assertEquals(listOf(0, 0), refreshed)
    }

    @Test
    fun ambiguousSetupWriteFailureStillAttemptsRestoreAndDoesNotActivate() {
        onCommand = {
            if (it.first() == "settings" && it.last() == "0") {
                error("Write timed out")
            }
        }
        assertFalse(engine().activate(false, 0).success)
        assertTrue(commands.any { it.first() == "settings" && it.last() == "1" })
        assertEquals(listOf(0), refreshed)
        assertFalse(commands.any { it.first() == "dpm" })
    }

    @Test
    fun restoreFailureIsShownWithoutHidingSuccessfulOwnership() {
        onCommand = {
            if (it.first() == "settings" && it.last() == "1") {
                error("Settings service unavailable")
            }
        }
        val result = engine().activate(false, 0)
        assertTrue(result.success)
        assertTrue(
            result.output.contains("could not restore the original setup state")
        )
        assertFalse(result.output.contains("Settings service unavailable"))
        assertEquals(listOf(0, 0), refreshed)
    }

    @Test
    fun unsupportedRefreshIsBestEffortAndReported() {
        onRefresh = { error("Unsupported on this ROM") }
        val result = engine().activate(false, 0)
        assertTrue(result.success)
        assertTrue(
            result.output.contains("optional account workaround is unavailable")
        )
        assertFalse(result.output.contains("Unsupported on this ROM"))
        assertTrue(commands.any { it.first() == "settings" && it.last() == "1" })
    }

    @Test
    fun missingHiddenRefreshMethodDoesNotInterruptSetupRestoration() {
        onRefresh = { throw NoSuchMethodError("OEM method unavailable") }
        val result = engine().activate(false, 0)
        assertTrue(result.success)
        assertTrue(
            result.output.contains("optional account workaround is unavailable")
        )
        assertFalse(result.output.contains("NoSuchMethodError"))
        assertTrue(commands.any { it.first() == "settings" && it.last() == "1" })
        assertEquals(listOf(0, 0), refreshed)
    }

    @Test
    fun restorationReadbackFailureIsReported() {
        var restoring = false
        onCommand = {
            if (it.first() == "settings" && it.last() == "1") {
                restoring = true
            }
            if (restoring && it.first() == "settings" && it[3] == "get") {
                error("Restored setting could not be read")
            }
        }
        val result = engine().activate(false, 0)
        assertTrue(result.success)
        assertTrue(
            result.output.contains("could not restore the original setup state")
        )
    }

    @Test
    fun androidBefore6CannotRunAnyActivationCommands() {
        for (sdk in 21..22) {
            commands.clear()
            refreshed.clear()
            val result = engine(sdk).activate(false, 0)
            assertFalse(result.success)
            assertTrue(result.output.contains("Android 6 or later"))
            assertTrue(commands.isEmpty())
            assertTrue(refreshed.isEmpty())
        }
    }

    @Test
    fun android6Through11UsesDpmWithoutReadingOrChangingSetupState() {
        for (sdk in 23..30) {
            for (profileOwner in listOf(false, true)) {
                commands.clear()
                refreshed.clear()
                assertTrue(engine(sdk).activate(profileOwner, 0).success)
                assertFalse(commands.any { it.first() == "settings" })
                assertTrue(refreshed.isEmpty())
                assertTrue(
                    listOf(
                        "dpm",
                        if (profileOwner) {
                            "set-profile-owner"
                        } else {
                            "set-device-owner"
                        },
                        "--user",
                        "0",
                        admin,
                    ) in commands
                )
            }
        }
    }

    @Test
    fun android12UsesTheSetupWorkaround() {
        assertTrue(engine(31).activate(false, 0).success)
        assertEquals(listOf(0, 0), refreshed)
        assertTrue(commands.any { it.first() == "settings" && it.last() == "0" })
        assertTrue(commands.any { it.first() == "settings" && it.last() == "1" })
    }

    @Test
    fun olderAndroidPreservesTheSystemStacktraceWithoutTheSetupWorkaround() {
        dpmResult =
            CommandResult(
                1,
                "java.lang.IllegalStateException: accounts on the device\n" +
                    "    at android.app.admin.DevicePolicyManager.setDeviceOwner(DPM.java:12)",
            )
        val result = engine(30).activate(false, 0)
        assertFalse(result.success)
        assertEquals(
            "java.lang.IllegalStateException: accounts on the device",
            result.output,
        )
        assertFalse(result.output.contains("DPM.java"))
        assertEquals(dpmResult.output, result.diagnostics)
        assertTrue(commands.any { it.first() == "dpm" })
        assertFalse(commands.any { it.first() == "settings" })
        assertTrue(refreshed.isEmpty())
    }
}
