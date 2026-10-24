package uk.nktnet.webviewkiosk.utils

import android.app.ActivityManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LockTaskRecoveryTest {
    @Before
    fun setUp() {
        LockTaskRecovery.resetForTests()
    }

    @After
    fun tearDown() {
        LockTaskRecovery.resetForTests()
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
    fun neverObservedFullLockCannotRecover() {
        assertFalse(shouldRecover())
        LockTaskRecovery.observeLockTaskState(ActivityManager.LOCK_TASK_MODE_PINNED)
        assertFalse(shouldRecover())
    }

    @Test
    fun screenPinningClearsPreviouslyObservedManagedLock() {
        LockTaskRecovery.onLockStarted()
        assertTrue(shouldRecover())
        LockTaskRecovery.observeLockTaskState(ActivityManager.LOCK_TASK_MODE_PINNED)
        LockTaskRecovery.observeLockTaskState(ActivityManager.LOCK_TASK_MODE_NONE)
        assertFalse(shouldRecover())
        LockTaskRecovery.observeLockTaskState(ActivityManager.LOCK_TASK_MODE_LOCKED)
        assertTrue(shouldRecover())
    }

    @Test
    fun disablingRecoveryDisarmsPreviousLock() {
        LockTaskRecovery.onLockStarted()
        assertTrue(shouldRecover())
        LockTaskRecovery.disarm()
        assertFalse(shouldRecover())
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

    @Test
    fun disabledOptionsDisarmImmediatelyEvenBeforeWatcherPolls() {
        LockTaskRecovery.onLockStarted()
        LockTaskRecovery.onSettingsChanged(
            relockEnabled = false,
            lockOnLaunch = true,
        )
        assertFalse(shouldRecover())
        LockTaskRecovery.onSettingsChanged(relockEnabled = true, lockOnLaunch = true)
        assertFalse(shouldRecover())

        LockTaskRecovery.onLockStarted()
        LockTaskRecovery.onSettingsChanged(
            relockEnabled = true,
            lockOnLaunch = false,
        )
        assertFalse(shouldRecover())
    }

    @Test
    fun observingManagedLockRearmsAfterSystemInitiatedRelock() {
        LockTaskRecovery.onLockStarted()
        val previous = LockTaskRecovery.beginIntentionalUnlock()
        LockTaskRecovery.completeIntentionalUnlock(true, previous)
        assertFalse(shouldRecover())

        LockTaskRecovery.observeLockTaskState(ActivityManager.LOCK_TASK_MODE_LOCKED)
        assertTrue(shouldRecover())
    }
}
