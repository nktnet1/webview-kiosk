package uk.nktnet.webviewkiosk.activation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationErrorsTest {
    @Test
    fun androidFailuresBecomeActionableMessagesWithoutStacktraces() {
        val cases =
            mapOf(
                "No device owner is configured" to "Activate Device Owner first",
                "device owner is not set" to "Activate Device Owner first",
                "device owner does not exist" to "Activate Device Owner first",
                "device owner doesn't exist" to "Activate Device Owner first",
                "DeviceOwner does not exist" to "Activate Device Owner first",
                "There are already several users" to
                    "Review them in Android Settings",
                "Trying to set the device owner, but device owner is already set" to
                    "An owner is already configured",
                "Unknown admin: ComponentInfo{example/Admin}" to "Reinstall the app",
                "java.lang.SecurityException: denied" to "Allow this app in Shizuku",
                "android.os.DeadObjectException" to "restart its service",
                "Shizuku is not running" to "start its service",
                "unrecognised OEM exception" to
                    "use ADB or factory reset and QR setup",
            )
        for ((error, expected) in cases) {
            val raw =
                "$error\n    at android.app.admin.DevicePolicyManager.run(DPM.java:42)"
            val result = activationFailure(IllegalStateException(raw))
            assertFalse(result.success)
            assertTrue(error, result.output.contains(expected))
            assertFalse(result.output.contains("DPM.java"))
            assertFalse(result.output.contains("java.lang"))
            assertTrue(result.diagnostics.contains(raw))
        }
    }

    @Test
    fun alreadyInAnAndroidRestrictionDoesNotImplyAnExistingOwner() {
        val accounts =
            activationFailure(
                IllegalStateException(
                    "Not allowed to set the device owner because there are already some accounts on the device"
                )
            )
        val users =
            activationFailure(
                IllegalStateException(
                    "Not allowed to set the device owner because there are already several users"
                )
            )
        assertTrue(accounts.output.contains("Remove conflicting accounts"))
        assertTrue(users.output.contains("Review them in Android Settings"))
    }

    @Test
    fun onlyExplicitAppMessagesAreShownVerbatim() {
        val message = "Open the app in the main user (ID 10) and try again."
        assertTrue(activationFailure(ActivationException(message)).output == message)
        assertFalse(
            activationFailure(IllegalStateException("Internal error: secret detail"))
                .output
                .contains("secret detail")
        )
    }
}
