package uk.nktnet.webviewkiosk.states

import com.rosan.dhizuku.api.DhizukuLockTaskModeListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DhizukuLockTaskModeMonitorTest {
    private val registry = FakeRegistry()
    private val events = mutableListOf<Boolean>()
    private var now = 0L
    private val monitor =
        DhizukuLockTaskModeMonitor(registry, { now }) { events.add(it) }

    @Test
    fun callbacksAreForwardedAndPeriodicRegistrationIsIdempotent() {
        monitor.refresh(allowed = true)
        val listener = registry.listener!!
        listener.onLockTaskModeChanged(true, "other.package")
        listener.onLockTaskModeChanged(false, null)
        assertEquals(listOf(true, false), events)

        now = 9999L
        monitor.refresh(allowed = true)
        assertEquals(1, registry.addCalls)

        // A refresh re-registers the same Binder after a server restart.
        now = 10_000L
        monitor.refresh(allowed = true)
        assertEquals(2, registry.addCalls)
        assertSame(listener, registry.listener)
    }

    @Test
    fun losingOwnerPermissionUnregistersAndDisablesLateCallbacks() {
        monitor.refresh(allowed = true)
        val oldListener = registry.listener!!
        monitor.refresh(allowed = false)
        assertEquals(1, registry.removeCalls)
        oldListener.onLockTaskModeChanged(true, "other.package")
        assertEquals(emptyList<Boolean>(), events)

        now = 1000L
        monitor.refresh(allowed = true)
        assertEquals(2, registry.addCalls)
        oldListener.onLockTaskModeChanged(true, "other.package")
        assertEquals(listOf(true), events)
    }

    @Test
    fun oldDhizukuServerFallsBackAndRetriesAfterUpgrade() {
        registry.addError = UnsupportedOperationException("old server")
        monitor.refresh(allowed = true)
        assertEquals(1, registry.addCalls)

        now = 59_999L
        monitor.refresh(allowed = true)
        assertEquals(1, registry.addCalls)

        registry.addError = null
        now = 60_000L
        monitor.refresh(allowed = true)
        assertEquals(2, registry.addCalls)
        registry.listener!!.onLockTaskModeChanged(true, "other.package")
        assertEquals(listOf(true), events)
    }

    @Test
    fun transientRegistrationFailureDoesNotStopRetries() {
        registry.addError = IllegalStateException("server disconnected")
        monitor.refresh(allowed = true)
        assertEquals(1, registry.addCalls)

        registry.addError = null
        now = 10_000L
        monitor.refresh(allowed = true)
        assertEquals(2, registry.addCalls)
    }

    @Test
    fun unregisterFailureDoesNotLeaveCallbacksEnabled() {
        monitor.refresh(allowed = true)
        val listener = registry.listener!!
        registry.removeError = IllegalStateException("server disconnected")
        monitor.refresh(allowed = false)
        listener.onLockTaskModeChanged(true, "other.package")
        assertEquals(emptyList<Boolean>(), events)
    }

    private class FakeRegistry : DhizukuLockTaskModeMonitor.Registry {
        var addCalls = 0
        var removeCalls = 0
        var listener: DhizukuLockTaskModeListener? = null
        var addError: Exception? = null
        var removeError: Exception? = null

        override fun add(listener: DhizukuLockTaskModeListener) {
            addCalls++
            addError?.let { throw it }
            this.listener = listener
        }

        override fun remove(listener: DhizukuLockTaskModeListener) {
            removeCalls++
            removeError?.let { throw it }
            if (this.listener === listener) {
                this.listener = null
            }
        }
    }
}
