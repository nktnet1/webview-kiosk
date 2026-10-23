package uk.nktnet.webviewkiosk.utils

import android.app.ActivityManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTaskRecoveryTest {
    @After
    fun tearDown() {
        LockTaskRecovery.onLockStarted()
    }

    private fun shouldRecover(
        enabled: Boolean = true,
        lockOnLaunch: Boolean = true,
        deviceOwner: Boolean = true,
        allowlisted: Boolean = true,
        mode: Int = ActivityManager.LOCK_TASK_MODE_NONE,
    ): Boolean =
        LockTaskRecovery.shouldRecover(
            enabled,
            lockOnLaunch,
            deviceOwner,
            allowlisted,
            mode,
        )

    @Test
    fun onlyUnexpectedFullLockTaskExitsAreEligible() {
        LockTaskRecovery.onLockStarted()
        assertTrue(shouldRecover())
        assertFalse(shouldRecover(enabled = false))
        assertFalse(shouldRecover(lockOnLaunch = false))
        assertFalse(shouldRecover(deviceOwner = false))
        assertFalse(shouldRecover(allowlisted = false))
        assertFalse(shouldRecover(mode = ActivityManager.LOCK_TASK_MODE_LOCKED))
        assertFalse(shouldRecover(mode = ActivityManager.LOCK_TASK_MODE_PINNED))
    }

    @Test
    fun explicitUnlockStaysSuppressedUntilRelocked() {
        LockTaskRecovery.onLockStarted()
        val previous = LockTaskRecovery.beginIntentionalUnlock()
        LockTaskRecovery.completeIntentionalUnlock(true, previous)
        assertFalse(shouldRecover())
        LockTaskRecovery.onLockStarted()
        assertTrue(shouldRecover())
    }

    @Test
    fun failedUnlockDoesNotSuppressRecovery() {
        LockTaskRecovery.onLockStarted()
        val previous = LockTaskRecovery.beginIntentionalUnlock()
        assertFalse(shouldRecover())
        LockTaskRecovery.completeIntentionalUnlock(false, previous)
        assertTrue(shouldRecover())
    }

    @Test
    fun repeatedFailedUnlockPreservesPreviousIntent() {
        LockTaskRecovery.onLockStarted()
        val first = LockTaskRecovery.beginIntentionalUnlock()
        LockTaskRecovery.completeIntentionalUnlock(true, first)
        val second = LockTaskRecovery.beginIntentionalUnlock()
        LockTaskRecovery.completeIntentionalUnlock(false, second)
        assertFalse(shouldRecover())
    }
}
