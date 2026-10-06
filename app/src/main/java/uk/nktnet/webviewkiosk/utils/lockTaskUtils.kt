package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.Android6KioskActivity
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.UnlockAuthRequirementOption
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton

private fun tryLockAction(
    activity: Activity,
    lockAction: Activity.() -> Unit,
    onSuccess: () -> Unit = {},
    onFailed: (String) -> Unit
): Boolean {
    return try {
        activity.lockAction()
        onSuccess()
        true
    } catch (e: SecurityException) {
        Log.e(Constants.APP_SCHEME, "Lock action error (security)", e)
        onFailed("[SecurityException] ${e.message}")
        false
    } catch (e: IllegalArgumentException) {
        Log.e(Constants.APP_SCHEME, "Lock action error (illegal argument)", e)
        onFailed("[IllegalArgumentException] ${e.message}")
        false
    } catch (e: Exception) {
        Log.e(Constants.APP_SCHEME, "Lock action error (unknown)", e)
        onFailed("[UnknownException] ${e.message}")
        false
    }
}

fun applyLockTaskFeatures(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        return
    }

    if (!DeviceOwnerManager.hasOwnerPermission(context)) {
        return
    }

    val userSettings = UserSettings(context)
    var features = DevicePolicyManager.LOCK_TASK_FEATURE_NONE

    if (userSettings.lockTaskFeatureHome) {
        features = features or DevicePolicyManager.LOCK_TASK_FEATURE_HOME

        if (userSettings.lockTaskFeatureOverview) {
            features = features or DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
        }

        if (userSettings.lockTaskFeatureNotifications) {
            features = features or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
        }
    }

    if (userSettings.lockTaskFeatureGlobalActions) {
        features = features or DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
    }

    if (userSettings.lockTaskFeatureSystemInfo) {
        features = features or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
    }
    if (userSettings.lockTaskFeatureKeyguard) {
        features = features or DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
    }
    if (
        userSettings.lockTaskFeatureBlockActivityStartInTask
        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    ) {
        features = features or DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK
    }

    try {
        DeviceOwnerManager.DPM.setLockTaskFeatures(DeviceOwnerManager.DAR, features)
    } catch (e: Exception) {
        Log.e(Constants.APP_SCHEME, "Failed to set lock task features", e)
    }
}

private enum class Android6LockTaskPreparation {
    CONTINUE,
    ALREADY_LOCKED,
    FAILED,
}

@RequiresApi(Build.VERSION_CODES.M)
fun launchAndroid6KioskTask(
    activity: Activity,
    fromHome: Boolean = false,
    lockRequested: Boolean = false,
) {
    Log.d(
        Constants.APP_SCHEME,
        "Android 6 kiosk redirect: activity=${activity.javaClass.simpleName} " +
            "taskId=${activity.taskId} fromHome=$fromHome lockRequested=$lockRequested"
    )
    val launchIntent = Intent(activity, Android6KioskActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        putExtra(Constants.INTENT_HOME_LAUNCH, fromHome)
        putExtra(Constants.INTENT_ANDROID6_LOCK_TASK, lockRequested)
    }
    activity.startActivity(launchIntent)

    // Hide the routing task, not the kiosk host. Keep the private task available in Recents
    // and do not clear or finish it when HOME is pressed after an unlock.
    try {
        val activityManager =
            activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION") // RecentTaskInfo.taskId is not available on API 23.
        val routingTask = activityManager.appTasks
            .firstOrNull { it.taskInfo?.id == activity.taskId }
        routingTask?.setExcludeFromRecents(true)
    } catch (e: Exception) {
        Log.w(Constants.APP_SCHEME, "Failed to hide Android 6 routing task from Recents", e)
    }
}

private fun logAndroid6LockAction(activity: Activity, action: String) {
    if (Build.VERSION.SDK_INT != Build.VERSION_CODES.M) {
        return
    }
    val activityManager =
        activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    Log.d(
        Constants.APP_SCHEME,
        "Android 6 $action: activity=${activity.javaClass.simpleName} " +
            "taskId=${activity.taskId} state=${activityManager.lockTaskModeState}"
    )
}

@RequiresApi(Build.VERSION_CODES.M)
private fun prepareAndroid6LockTask(activity: Activity): Android6LockTaskPreparation {
    val isDeviceOwner = try {
        DeviceOwnerManager.DPM.isDeviceOwnerApp(activity.packageName)
    } catch (e: Exception) {
        Log.w(Constants.APP_SCHEME, "Failed to check Android 6 device owner state", e)
        false
    }

    if (!isDeviceOwner) {
        return Android6LockTaskPreparation.CONTINUE
    }

    val lockTaskPermitted = try {
        if (!DeviceOwnerManager.DPM.isLockTaskPermitted(activity.packageName)) {
            setupLockTaskPackage(activity)
        }
        DeviceOwnerManager.DPM.isLockTaskPermitted(activity.packageName)
    } catch (e: Exception) {
        Log.e(Constants.APP_SCHEME, "Failed to verify Android 6 lock task permission", e)
        false
    }

    if (!lockTaskPermitted) {
        ToastManager.show(
            activity,
            "Failed to lock: app is not permitted for lock task mode."
        )
        return Android6LockTaskPreparation.FAILED
    }

    val activityManager =
        activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    return when (activityManager.lockTaskModeState) {
        ActivityManager.LOCK_TASK_MODE_LOCKED -> Android6LockTaskPreparation.ALREADY_LOCKED
        ActivityManager.LOCK_TASK_MODE_PINNED -> {
            val unpinned = tryLockAction(
                activity,
                lockAction = Activity::stopLockTask,
                onFailed = {
                    ToastManager.show(activity, "Failed to leave screen pinning: $it")
                }
            )
            if (unpinned) {
                Android6LockTaskPreparation.CONTINUE
            } else {
                Android6LockTaskPreparation.FAILED
            }
        }
        else -> Android6LockTaskPreparation.CONTINUE
    }
}

fun tryLockTask(activity: Activity?): Boolean {
    if (activity == null) {
        return false
    }

    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
        if (activity.javaClass == MainActivity::class.java) {
            // MainActivity may have been reused as HOME even after an app-menu launch.
            // Only lock the separate task; the resumed host consumes this explicit request.
            return tryLockAction(
                activity,
                lockAction = { launchAndroid6KioskTask(this, lockRequested = true) },
                onFailed = { ToastManager.show(activity, "Failed to lock: $it") }
            )
        }

        when (prepareAndroid6LockTask(activity)) {
            Android6LockTaskPreparation.ALREADY_LOCKED -> {
                AuthenticationManager.resetAuthentication()
                return true
            }
            Android6LockTaskPreparation.FAILED -> return false
            Android6LockTaskPreparation.CONTINUE -> Unit
        }
    }

    applyLockTaskFeatures(activity)
    logAndroid6LockAction(activity, "startLockTask")
    return tryLockAction(
        activity,
        lockAction = Activity::startLockTask,
        onSuccess = {
            AuthenticationManager.resetAuthentication()
            // Handled MQTT publish in LockStateSingleton
        },
        onFailed = {
            ToastManager.show(activity, "Failed to lock: $it")
        }
    )
}

fun tryUnlockTask(activity: Activity?): Boolean {
    if (activity == null) {
        return false
    }
    logAndroid6LockAction(activity, "stopLockTask")
    return tryLockAction(
        activity,
        lockAction = {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && DeviceOwnerManager.hasOwnerPermission(activity)
            ) {
                try {
                    val savedPackages = DeviceOwnerManager.DPM.getLockTaskPackages(
                        DeviceOwnerManager.DAR
                    )
                    val savedFeatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        DeviceOwnerManager.DPM.getLockTaskFeatures(
                            DeviceOwnerManager.DAR
                        )
                    } else {
                        null
                    }
                    DeviceOwnerManager.DPM.setLockTaskPackages(
                        DeviceOwnerManager.DAR,
                        arrayOf(activity.packageName)
                    )
                    DeviceOwnerManager.DPM.setLockTaskPackages(
                        DeviceOwnerManager.DAR,
                        savedPackages,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && savedFeatures != null) {
                        DeviceOwnerManager.DPM.setLockTaskFeatures(
                            DeviceOwnerManager.DAR,
                            savedFeatures,
                        )
                    }
                } catch (e: Exception) {
                    Log.w(
                        Constants.APP_SCHEME,
                        "Lock task packages or features operation failed",
                        e
                    )
                    ToastManager.show(
                        activity,
                        "DPM ${DeviceOwnerManager.status.value.mode}) error: ${e.message}"
                    )
                }
            }
            activity.stopLockTask()
        },
        onSuccess = {
            // Handled MQTT publish in LockStateSingleton
        },
        onFailed = {
            ToastManager.show(activity, "Failed to unlock: $it")
        }
    )
}

fun setupLockTaskPackage(context: Context): Boolean {
    try {
        if (!DeviceOwnerManager.hasOwnerPermission(context)){
            return false
        }
        val packages =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val current = DeviceOwnerManager.DPM.getLockTaskPackages(
                    DeviceOwnerManager.DAR
                ).toMutableSet()
                current.add(context.packageName)
                current.toTypedArray()
            } else {
                arrayOf(context.packageName)
            }
        DeviceOwnerManager.DPM.setLockTaskPackages(
            DeviceOwnerManager.DAR,
            packages
        )

        updateDeviceSettings(context)
        return true
    } catch (e: Exception) {
        Log.e(Constants.APP_SCHEME, "Failed to setup lock task packages", e)
        return false
    }
}

fun getIsLocked(activityManager: ActivityManager): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    } else {
        @Suppress("DEPRECATION")
        return activityManager.isInLockTaskMode
    }
}

fun requireAuthForUnlock(context: Context, userSettings: UserSettings): Boolean {
    if (userSettings.unlockAuthRequirement == UnlockAuthRequirementOption.OFF) {
        return false
    }
    if (userSettings.unlockAuthRequirement == UnlockAuthRequirementOption.REQUIRE) {
        return true
    }
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    return dpm.isLockTaskPermitted(context.packageName)
}

fun unlockWithAuthIfRequired(activity: Activity) {
    val userSettings = UserSettings(activity)

    if (requireAuthForUnlock(activity, userSettings)) {
        WaitingForUnlockStateSingleton.startWaiting()
        AuthenticationManager.showAuthenticationPrompt(
            title = "Authentication Required",
            description = "Please authenticate to unlock ${activity.getString(R.string.app_name)}"
        )
    } else {
        tryUnlockTask(activity)
        CoroutineScope(Dispatchers.Main).launch {
            WaitingForUnlockStateSingleton.emitUnlockSuccess()
        }
    }
}
