package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowDevicePolicyManager
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.handlers.RemoteInboundHandler
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ManagedStatusBarPolicyShadow::class])
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

    @Test
    fun remoteSettingsEnableAndDisablePolicyWithoutAnActivity() {
        val dpm =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
        val admin = ComponentName(context, WebviewKioskAdminReceiver::class.java)
        shadowOf(dpm).setActiveAdmin(admin)
        shadowOf(dpm).setDeviceOwner(admin)
        dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
        shadowOf(activityManager)
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED)
        settings.disableStatusBarOutsideLockTask = false

        fun sendRemoteSetting(value: Boolean) {
            RemoteInboundHandler.handleInboundSettings(
                context,
                InboundSettingsMessage(
                    showToast = false,
                    reloadActivity = false,
                    data =
                        InboundSettingsMessage.SettingsUpdateData(
                            settings =
                                buildJsonObject {
                                    put(
                                        UserSettingsKeys.Device.Owner
                                            .DISABLE_STATUS_BAR_OUTSIDE_LOCK_TASK,
                                        value,
                                    )
                                }
                        ),
                ),
                RemoteMessageManager.RemoteMessage.Source.MQTT,
            )
        }

        sendRemoteSetting(true)
        assertTrue(UserSettings(context).disableStatusBarOutsideLockTask)
        assertTrue(DeviceOwnerStatusBarPolicy.isManaged(context))
        val shadow = shadowOf(dpm) as ManagedStatusBarPolicyShadow
        assertTrue(shadow.statusBarDisabled)
        assertTrue(shadow.ownerMarkerPresentWhenEnabled)

        // The service must also be able to restore access while kiosk mode is
        // unexpectedly ended, without relying on an Activity to resume.
        shadowOf(activityManager)
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE)
        sendRemoteSetting(false)
        assertFalse(UserSettings(context).disableStatusBarOutsideLockTask)
        assertFalse(DeviceOwnerStatusBarPolicy.isManaged(context))
        assertFalse(
            (shadowOf(dpm) as ManagedStatusBarPolicyShadow).statusBarDisabled
        )
    }

    @Test
    fun rejectedPolicyEnableDoesNotLeaveAnOwnershipMarker() {
        val dpm =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
        val admin = ComponentName(context, WebviewKioskAdminReceiver::class.java)
        shadowOf(dpm).setActiveAdmin(admin)
        shadowOf(dpm).setDeviceOwner(admin)
        dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
        shadowOf(activityManager)
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED)

        val shadow = shadowOf(dpm) as ManagedStatusBarPolicyShadow
        shadow.rejectNextRequest = true
        assertFalse(DeviceOwnerStatusBarPolicy.reconcile(context))
        assertTrue(shadow.ownerMarkerPresentWhenEnabled)
        assertFalse(DeviceOwnerStatusBarPolicy.isManaged(context))
        assertFalse(shadow.statusBarDisabled)
    }

    @Test
    fun removingOwnAllowlistEntryClearsManagedStatusBarAndSuppressesRelock() {
        val dpm =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
        val admin = ComponentName(context, WebviewKioskAdminReceiver::class.java)
        shadowOf(dpm).setActiveAdmin(admin)
        shadowOf(dpm).setDeviceOwner(admin)
        dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
        DeviceOwnerManager.init(context)
        shadowOf(activityManager)
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED)
        assertTrue(DeviceOwnerStatusBarPolicy.reconcile(context))
        assertTrue(DeviceOwnerStatusBarPolicy.isManaged(context))
        LockTaskRecovery.onLockStarted()

        removeLockTaskPackage(context, context.packageName, emptyArray())

        assertFalse(dpm.isLockTaskPermitted(context.packageName))
        assertFalse(DeviceOwnerStatusBarPolicy.isManaged(context))
        assertFalse(
            (shadowOf(dpm) as ManagedStatusBarPolicyShadow).statusBarDisabled
        )
        assertTrue(LockTaskRecovery.isIntentionallyUnlocked())
    }
}

/** Robolectric omits the Device Owner status-bar policy API on its stock shadow. */
@Implements(DevicePolicyManager::class)
class ManagedStatusBarPolicyShadow : ShadowDevicePolicyManager() {
    var statusBarDisabled = false
        private set

    var ownerMarkerPresentWhenEnabled = false
        private set

    var rejectNextRequest = false

    @Implementation
    protected fun setStatusBarDisabled(
        admin: ComponentName,
        disabled: Boolean,
    ): Boolean {
        if (disabled) {
            val context = RuntimeEnvironment.getApplication()
            ownerMarkerPresentWhenEnabled =
                context
                    .getSharedPreferences(
                        "device_owner_status_bar_policy",
                        Context.MODE_PRIVATE,
                    )
                    .getBoolean("managed_by_webview_kiosk", false)
        }
        if (rejectNextRequest) {
            rejectNextRequest = false
            return false
        }
        statusBarDisabled = disabled
        return true
    }
}
