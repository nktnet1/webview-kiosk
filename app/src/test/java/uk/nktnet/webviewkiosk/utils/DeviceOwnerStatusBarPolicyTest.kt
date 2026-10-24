package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeviceOwnerStatusBarPolicyTest {
    class FailedStartActivity : Activity() {
        override fun startLockTask() {
            throw SecurityException("Lock task start rejected")
        }
    }

    private lateinit var context: Context
    private lateinit var settings: UserSettings
    private lateinit var activityManager: ActivityManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences(
                "device_owner_status_bar_policy",
                Context.MODE_PRIVATE,
            )
            .edit()
            .clear()
            .commit()
        LockTaskRecovery.resetForTests()
        settings =
            UserSettings(context).apply {
                lockOnLaunch = true
                disableStatusBarOutsideLockTask = true
            }
        activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    @Test
    fun noInitialPolicyOutsideFullLockTask() {
        shadowOf(activityManager)
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE)
        assertTrue(DeviceOwnerStatusBarPolicy.reconcile(context))
        assertFalse(DeviceOwnerStatusBarPolicy.isManaged(context))

        shadowOf(activityManager)
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_PINNED)
        assertTrue(DeviceOwnerStatusBarPolicy.reconcile(context))
        assertFalse(DeviceOwnerStatusBarPolicy.isManaged(context))
    }

    @Test
    fun unexpectedExitDoesNotClearPreviouslyManagedPolicy() {
        context
            .getSharedPreferences(
                "device_owner_status_bar_policy",
                Context.MODE_PRIVATE,
            )
            .edit()
            .putBoolean("managed_by_webview_kiosk", true)
            .commit()
        shadowOf(activityManager)
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE)

        assertTrue(DeviceOwnerStatusBarPolicy.reconcile(context))
        assertTrue(DeviceOwnerStatusBarPolicy.isManaged(context))
    }

    @Test
    fun failedRelockPreservesPreviouslyAppliedStatusBarPolicy() {
        val controller =
            Robolectric.buildActivity(FailedStartActivity::class.java)
                .create()
                .start()
                .resume()
        val activity = controller.get()
        DeviceOwnerManager.init(activity)
        context
            .getSharedPreferences(
                "device_owner_status_bar_policy",
                Context.MODE_PRIVATE,
            )
            .edit()
            .putBoolean("managed_by_webview_kiosk", true)
            .commit()

        assertFalse(tryLockTask(activity))
        assertTrue(DeviceOwnerStatusBarPolicy.isManaged(activity))
        controller.pause().stop().destroy()
    }

    @Test
    fun directEnableIsRejectedOutsideFullLockTask() {
        val dpm =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
        val admin = ComponentName(context, WebviewKioskAdminReceiver::class.java)
        shadowOf(dpm).setActiveAdmin(admin)
        shadowOf(dpm).setDeviceOwner(admin)
        dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
        assertTrue(dpm.isDeviceOwnerApp(context.packageName))
        assertTrue(dpm.isLockTaskPermitted(context.packageName))
        shadowOf(activityManager)
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_PINNED)
        assertFalse(DeviceOwnerStatusBarPolicy.sync(context, true))
        assertFalse(DeviceOwnerStatusBarPolicy.isManaged(context))
    }
}
