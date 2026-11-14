package uk.nktnet.webviewkiosk.states

import android.os.SystemClock
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuLockTaskModeListener

/**
 * Registers for Dhizuku callbacks without making the polling fallback depend on
 * Binder.
 */
internal class DhizukuLockTaskModeMonitor(
    private val registry: Registry = PlatformRegistry,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val onChanged: (Boolean) -> Unit,
) {
    interface Registry {
        fun add(listener: DhizukuLockTaskModeListener)

        fun remove(listener: DhizukuLockTaskModeListener)
    }

    private object PlatformRegistry : Registry {
        override fun add(listener: DhizukuLockTaskModeListener) =
            Dhizuku.addLockTaskModeListener(listener)

        override fun remove(listener: DhizukuLockTaskModeListener) =
            Dhizuku.removeLockTaskModeListener(listener)
    }

    private val listener =
        object : DhizukuLockTaskModeListener() {
            override fun onLockTaskModeChanged(entering: Boolean, pkg: String?) {
                if (enabled) {
                    onChanged(entering)
                }
            }
        }

    @Volatile private var enabled = false
    private var registered = false
    private var nextAttemptAt = 0L

    /** Called from a single coroutine, separately from the one-second state poll. */
    fun refresh(allowed: Boolean) {
        enabled = allowed
        if (!allowed) {
            if (registered) {
                try {
                    registry.remove(listener)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove Dhizuku lock task listener", e)
                }
                registered = false
            }
            nextAttemptAt = 0L
            return
        }

        val now = clock()
        if (now < nextAttemptAt) {
            return
        }
        nextAttemptAt = now + REFRESH_INTERVAL_MS
        try {
            // The server deduplicates registrations by listener Binder. Refreshing
            // also re-registers after a Dhizuku server restart.
            registry.add(listener)
            registered = true
        } catch (e: UnsupportedOperationException) {
            // Older Dhizuku servers have no listener endpoint. Polling continues.
            registered = false
            nextAttemptAt = now + UNSUPPORTED_RETRY_MS
        } catch (e: Exception) {
            registered = false
            Log.w(TAG, "Failed to register Dhizuku lock task listener", e)
        }
    }

    private companion object {
        const val TAG = "DhizukuLockMonitor"
        const val REFRESH_INTERVAL_MS = 10_000L
        const val UNSUPPORTED_RETRY_MS = 60_000L
    }
}
