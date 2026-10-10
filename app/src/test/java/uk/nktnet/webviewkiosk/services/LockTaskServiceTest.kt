package uk.nktnet.webviewkiosk.services

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import uk.nktnet.webviewkiosk.managers.CustomNotificationChannel
import uk.nktnet.webviewkiosk.managers.CustomNotificationType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.LEGACY)
class LockTaskServiceTest {
    private lateinit var context: Context
    private lateinit var controller: ServiceController<LockTaskService>
    private lateinit var service: LockTaskService
    private var destroyed = false

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Android grants the app's declared signature permission to its own UID.
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        val activityManager = context.getSystemService(ActivityManager::class.java)
        shadowOf(activityManager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED)
        controller = Robolectric.buildService(LockTaskService::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDown() {
        if (!destroyed) controller.destroy()
    }

    @Test
    fun nullIntentRestartCreatesItsNotificationChannelWithoutAnActivity() {
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(null, manager.getNotificationChannel(CustomNotificationChannel.LockTaskMode.ID))

        assertEquals(Service.START_STICKY, start())

        assertNotNull(manager.getNotificationChannel(CustomNotificationChannel.LockTaskMode.ID))
        val notification = shadowOf(service).lastForegroundNotification
        assertEquals(CustomNotificationType.LOCK_TASK_MODE, shadowOf(service).lastForegroundNotificationId)
        assertEquals(CustomNotificationChannel.LockTaskMode.ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(notification.contentIntent)
    }

    @Test
    @Config(sdk = [29])
    fun nullIntentRestartCreatesItsChannelOnTheApi29ForegroundPath() {
        assertEquals(Service.START_STICKY, start())
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(CustomNotificationChannel.LockTaskMode.ID))
    }

    @Test
    fun repeatedStartsRegisterOnlyOneReturnReceiver() {
        start()
        assertEquals(Service.START_STICKY, start(2))

        assertEquals(1, shadowOf(RuntimeEnvironment.getApplication())
            .getReceiversForIntent(Intent(LockTaskService.RETURN_ACTION)).size)
        assertEquals(CustomNotificationType.LOCK_TASK_MODE, shadowOf(service).lastForegroundNotificationId)
    }

    @Test
    fun foregroundDenialDoesNotLeaveAReturnReceiverUntilDestroy() {
        shadowOf(service).setThrowInStartForeground(SecurityException("foreground denied"))

        assertEquals(Service.START_NOT_STICKY, start(7))

        assertEquals(7, shadowOf(service).stopSelfId)
        assertFalse(hasReturnReceiver())
    }

    @Test
    fun denialOfARepeatedStartRemovesThePreviousReceiverImmediately() {
        start()
        shadowOf(service).setThrowInStartForeground(SecurityException("foreground revoked"))

        assertEquals(Service.START_NOT_STICKY, start(8))

        assertFalse(hasReturnReceiver())
        assertTrue(shadowOf(service).isForegroundStopped)
    }

    @Test
    fun returnBroadcastStopsMonitoringAndUnregistersImmediately() {
        start()

        context.sendBroadcast(Intent(LockTaskService.RETURN_ACTION).setPackage(context.packageName))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(hasReturnReceiver())
        assertTrue(shadowOf(service).isForegroundStopped)
    }

    @Test
    fun destructionAfterAStartUnregistersTheReceiver() {
        start()
        controller.destroy()
        destroyed = true

        assertFalse(hasReturnReceiver())
    }

    private fun start(startId: Int = 1) = service.onStartCommand(null, 0, startId)

    private fun hasReturnReceiver() = shadowOf(RuntimeEnvironment.getApplication())
        .hasReceiverForIntent(Intent(LockTaskService.RETURN_ACTION))
}
