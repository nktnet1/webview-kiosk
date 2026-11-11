package uk.nktnet.webviewkiosk.activation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationErrorsTest {
    @Test
    fun summaryUsesTheOriginalFirstNonblankLine() {
        val raw =
            "\n  Error: device owner is already set\n" +
                "    at android.app.admin.DevicePolicyManager.run(DPM.java:42)"
        val result = activationFailure(IllegalStateException(raw))
        assertFalse(result.success)
        assertEquals("Error: device owner is already set", result.output)
        assertFalse(result.output.contains("DPM.java"))
        assertTrue(result.diagnostics.contains(raw))
    }

    @Test
    fun platformMessagesAreNotReplacedWithTroubleshootingAdvice() {
        val message = "Not allowed to set the device owner: accounts on the device"
        assertEquals(
            message,
            activationFailure(IllegalStateException(message)).output,
        )
    }

    @Test
    fun expandedDetailsRetainExceptionFramesCausesAndSuppressedErrors() {
        val cause = IllegalStateException("Device owner does not exist")
        val failure = IllegalStateException("Activation request failed", cause)
        failure.addSuppressed(IllegalStateException("Cleanup failed"))
        val result = activationFailure(failure)
        assertEquals("Activation request failed", result.output)
        assertTrue(
            result.diagnostics.contains(
                "java.lang.IllegalStateException: Activation request failed"
            )
        )
        assertTrue(
            result.diagnostics.contains(
                "Caused by: java.lang.IllegalStateException: Device owner does not exist"
            )
        )
        assertTrue(
            result.diagnostics.contains(
                "Suppressed: java.lang.IllegalStateException: Cleanup failed"
            )
        )
        assertTrue(result.diagnostics.contains("\tat "))
    }

    @Test
    fun appPreconditionsKeepTheirMessage() {
        val message = "Open the app in the main user (ID 10) and try again."
        assertEquals(message, activationFailure(ActivationException(message)).output)
    }

    @Test
    fun absentOrBlankMessagesUseAShortFallbackAndKeepTheException() {
        for (message in listOf(null, "", " \n\t")) {
            val result = activationFailure(IllegalStateException(message))
            assertEquals("Could not activate ownership.", result.output)
            assertTrue(
                result.diagnostics.contains("java.lang.IllegalStateException")
            )
        }
    }

    @Test
    fun longMessagesStayInDetailsRatherThanFillingTheSummary() {
        val raw = "Android rejected activation: " + "detail ".repeat(1000)
        val result = activationFailure(IllegalStateException(raw))
        assertTrue(result.output.startsWith("Android rejected activation:"))
        assertTrue(result.output.length <= 300)
        assertTrue(result.diagnostics.contains(raw))
    }
}
