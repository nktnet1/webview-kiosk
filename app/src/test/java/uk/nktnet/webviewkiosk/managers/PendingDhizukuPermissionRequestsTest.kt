package uk.nktnet.webviewkiosk.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDhizukuPermissionRequestsTest {
    @Test
    fun latePermissionResultCannotCompleteReplacementRequest() {
        val requests = PendingDhizukuPermissionRequests()
        val first = requests.replace()
        val replacement = requests.replace()

        assertFalse(requests.consume(first))
        assertTrue(requests.consume(replacement))
    }

    @Test
    fun obsoletePreflightCannotBeginRequestAfterReplacement() {
        val requests = PendingDhizukuPermissionRequests()
        val first = requests.replace()
        val replacement = requests.replace()

        assertFalse(requests.isCurrent(first))
        assertTrue(requests.isCurrent(replacement))
        assertFalse(requests.consume(first))
        assertTrue(requests.consume(replacement))
        assertFalse(requests.isCurrent(replacement))
    }

    @Test
    fun duplicateBinderCallbackCanCompleteOnlyOnce() {
        val requests = PendingDhizukuPermissionRequests()
        val request = requests.replace()

        assertTrue(requests.consume(request))
        assertFalse(requests.consume(request))
    }

    @Test
    fun invalidatedPermissionPromptCannotDeliverAResult() {
        val requests = PendingDhizukuPermissionRequests()
        val request = requests.replace()

        requests.invalidate()

        assertFalse(requests.consume(request))
        assertTrue(requests.consume(requests.replace()))
    }
}
