package uk.nktnet.webviewkiosk.managers

import java.util.concurrent.atomic.AtomicReference

/** Owns the latest Dhizuku permission prompt across asynchronous binder callbacks. */
internal class PendingDhizukuPermissionRequests {
    private val active = AtomicReference<Any?>(null)

    fun replace(): Any = Any().also { active.set(it) }

    /** Consuming the token before invoking a callback also rejects duplicate deliveries. */
    fun consume(token: Any): Boolean = active.compareAndSet(token, null)

    /** A replaced request must not begin an obsolete binder permission prompt. */
    fun isCurrent(token: Any): Boolean = active.get() === token

    fun invalidate() {
        active.set(null)
    }
}
