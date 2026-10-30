package uk.nktnet.webviewkiosk.utils

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.UserSettings

/**
 * Persists a status-bar policy across unexpected lock-task exits. Android defers the
 * policy while lock task is active and applies it as soon as that mode ends.
 */
internal object DeviceOwnerStatusBarPolicy {
    private const val PREFS_NAME = "device_owner_status_bar_policy"
    private const val MANAGED_KEY = "managed_by_webview_kiosk"

    fun isManaged(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(MANAGED_KEY, false)

    /**
     * Apply the configured policy only when full managed lock task is verified. Do
     * not remove an existing policy merely because the OS ended lock task.
     */
    fun reconcile(context: Context): Boolean {
        val settings = UserSettings(context)
        if (!settings.lockOnLaunch || !settings.disableStatusBarOutsideLockTask) {
            return sync(context, false)
        }
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                LockTaskRecovery.isIntentionallyUnlocked()
        ) {
            return true
        }
        val mode =
            try {
                (context.getSystemService(Context.ACTIVITY_SERVICE)
                        as ActivityManager)
                    .lockTaskModeState
            } catch (e: Exception) {
                Log.w(javaClass.simpleName, "Unable to read lock task mode", e)
                return false
            }
        return if (mode == ActivityManager.LOCK_TASK_MODE_LOCKED) {
            sync(context, true)
        } else {
            // Keep existing protection after an unexpected exit. Never install
            // it for a failed startup or an ordinary screen-pinning session.
            true
        }
    }

    data class PolicyClearResult(val succeeded: Boolean, val wasManaged: Boolean)

    /**
     * Reads and clears the policy under the same lock as installation. A concurrent
     * remote settings update must not install the policy between the caller's
     * managed-state check and a successful Unlock. Call only after beginning the
     * intentional-unlock suppression.
     */
    @Synchronized
    fun clearForIntentionalExit(context: Context): PolicyClearResult {
        val wasManaged = isManaged(context)
        return PolicyClearResult(
            // Keep Android 5.x and installations with no managed policy
            // unlockable without requiring an API 23 Device Owner call.
            succeeded = !wasManaged || sync(context, false),
            wasManaged = wasManaged,
        )
    }

    // Serialize binder updates with Unlock and ownership changes. Otherwise a
    // background settings update can re-disable the shade after Unlock cleared it.
    @Synchronized
    fun sync(context: Context, disabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!disabled && !prefs.getBoolean(MANAGED_KEY, false)) {
            return true // Preserve policies that this app did not install.
        }

        return try {
            val dpm =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as DevicePolicyManager
            if (!dpm.isDeviceOwnerApp(context.packageName)) {
                return false // Do not apply this policy through Dhizuku/profile
                // owner.
            }
            // Only a verified full lock is allowed to install this policy.
            // In particular, an explicit Unlock must never race with a refresh.
            if (disabled) {
                val mode =
                    (context.getSystemService(Context.ACTIVITY_SERVICE)
                            as ActivityManager)
                        .lockTaskModeState
                if (
                    LockTaskRecovery.isIntentionallyUnlocked() ||
                        !dpm.isLockTaskPermitted(context.packageName) ||
                        mode != ActivityManager.LOCK_TASK_MODE_LOCKED
                ) {
                    Log.w(
                        javaClass.simpleName,
                        "Status bar policy requires full lock task",
                    )
                    return false
                }
            }
            val admin = ComponentName(context, WebviewKioskAdminReceiver::class.java)
            val wasManaged = prefs.getBoolean(MANAGED_KEY, false)
            if (disabled && !wasManaged) {
                // Record ownership durably before calling Android. Otherwise a
                // process death after the binder call can strand a restriction
                // that a later Unlock believes we do not own.
                if (!prefs.edit().putBoolean(MANAGED_KEY, true).commit()) {
                    Log.w(
                        javaClass.simpleName,
                        "Unable to record status bar policy ownership",
                    )
                    return false
                }
            }
            if (!dpm.setStatusBarDisabled(admin, disabled)) {
                if (disabled && !wasManaged) {
                    prefs.edit().putBoolean(MANAGED_KEY, false).commit()
                }
                Log.w(
                    javaClass.simpleName,
                    "Device Owner status bar policy was rejected",
                )
                false
            } else if (!disabled) {
                // Once Android accepts removal, clear the ownership marker.
                prefs.edit().putBoolean(MANAGED_KEY, false).commit()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.w(
                javaClass.simpleName,
                "Unable to update Device Owner status bar policy",
                e,
            )
            false
        }
    }
}
