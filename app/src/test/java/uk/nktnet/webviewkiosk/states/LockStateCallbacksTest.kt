package uk.nktnet.webviewkiosk.states

import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LockStateCallbacksTest {
    private val app = RuntimeEnvironment.getApplication()
    private val receiver = WebviewKioskAdminReceiver()

    @Before
    fun setUp() {
        LockStateSingleton.onLockTaskModeChanged(false)
    }

    @After
    fun tearDown() {
        LockStateSingleton.onLockTaskModeChanged(false)
    }

    @Test
    fun nativeDpcCallbacksUpdateSharedState() {
        receiver.onLockTaskModeEntering(
            app,
            Intent(DeviceAdminReceiver.ACTION_LOCK_TASK_ENTERING),
            app.packageName,
        )
        assertTrue(LockStateSingleton.isLocked.value)

        receiver.onLockTaskModeExiting(
            app,
            Intent(DeviceAdminReceiver.ACTION_LOCK_TASK_EXITING),
        )
        assertFalse(LockStateSingleton.isLocked.value)
    }

    @Test
    fun pollingCorrectsMissedOrStaleCallbacks() {
        val manager =
            app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED)

        LockStateSingleton.refreshFromActivityManager(manager)
        assertTrue(LockStateSingleton.isLocked.value)

        LockStateSingleton.onLockTaskModeChanged(false)
        LockStateSingleton.refreshFromActivityManager(manager)
        assertTrue(LockStateSingleton.isLocked.value)

        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE)
        LockStateSingleton.refreshFromActivityManager(manager)
        assertFalse(LockStateSingleton.isLocked.value)
    }

    @Test
    fun repeatedCallbacksDoNotToggleState() {
        LockStateSingleton.onLockTaskModeChanged(true)
        LockStateSingleton.onLockTaskModeChanged(true)
        assertTrue(LockStateSingleton.isLocked.value)
        LockStateSingleton.onLockTaskModeChanged(false)
        LockStateSingleton.onLockTaskModeChanged(false)
        assertFalse(LockStateSingleton.isLocked.value)
    }
}
