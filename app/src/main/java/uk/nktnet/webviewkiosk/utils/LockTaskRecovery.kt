package uk.nktnet.webviewkiosk.utils

import android.app.ActivityManager

/** Keeps an explicit unlock from being mistaken for an unexpected firmware exit. */
internal object LockTaskRecovery {
    @Volatile private var intentionalUnlock = false
    @Volatile private var observedFullLockTask = false

    fun beginIntentionalUnlock(): Boolean {
        val previous = intentionalUnlock
        intentionalUnlock = true
        return previous
    }

    fun completeIntentionalUnlock(succeeded: Boolean, previous: Boolean) {
        if (!succeeded) {
            intentionalUnlock = previous
        }
    }

    fun onLockStarted() {
        observedFullLockTask = true
        intentionalUnlock = false
    }

    /** Changing the opt-in settings must not arm recovery for earlier exits. */
    fun disarm() {
        observedFullLockTask = false
    }

    fun onSettingsChanged(relockEnabled: Boolean, lockOnLaunch: Boolean) {
        if (!relockEnabled || !lockOnLaunch) {
            disarm()
        }
    }

    /** Screen pinning is not a managed lock session, even if it later ends. */
    fun observeLockTaskState(mode: Int) {
        when (mode) {
            // A real managed lock supersedes an older intentional Unlock,
            // including when the system initiated locking rather than this app.
            ActivityManager.LOCK_TASK_MODE_LOCKED -> onLockStarted()
            ActivityManager.LOCK_TASK_MODE_PINNED -> observedFullLockTask = false
        }
    }

    internal fun resetForTests() {
        intentionalUnlock = false
        observedFullLockTask = false
    }

    fun shouldRecover(
        enabled: Boolean,
        lockOnLaunch: Boolean,
        deviceOwner: Boolean,
        allowlisted: Boolean,
        lockTaskModeState: Int,
    ): Boolean =
        enabled &&
            lockOnLaunch &&
            deviceOwner &&
            allowlisted &&
            !intentionalUnlock &&
            observedFullLockTask &&
            lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE

    fun isIntentionallyUnlocked(): Boolean = intentionalUnlock
}
