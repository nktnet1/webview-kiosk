package uk.nktnet.webviewkiosk.utils

import android.app.ActivityManager

/** Keeps an explicit unlock from being mistaken for an unexpected firmware exit. */
internal object LockTaskRecovery {
    @Volatile private var intentionalUnlock = false

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
        intentionalUnlock = false
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
            lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE

    fun isIntentionallyUnlocked(): Boolean = intentionalUnlock
}
