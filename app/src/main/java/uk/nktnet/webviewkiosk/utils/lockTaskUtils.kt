package com.nktnet.webview_kiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.managers.AuthenticationManager
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.UnlockAuthRequirementOption
import com.nktnet.webview_kiosk.managers.DeviceOwnerManager
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.states.WaitingForUnlockStateSingleton

private fun tryLockAction(
    activity: Activity,
    action: Activity.() -> Unit,
    onSuccess: () -> Unit = {},
    onFailed: (String) -> Unit
): Boolean {
    return try {
        activity.action()
        onSuccess()
        true
    } catch (e: SecurityException) {
        e.printStackTrace()
        onFailed("[SecurityException] ${e.message}")
        false
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
        onFailed("[IllegalArgumentException] ${e.message}")
        false
    } catch (e: Exception) {
        e.printStackTrace()
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
        e.printStackTrace()
    }
}

fun tryLockTask(activity: Activity?): Boolean {
    if (activity == null) {
        return false
    }
    applyLockTaskFeatures(activity)
    return tryLockAction(
        activity,
        Activity::startLockTask,
        onSuccess = {
            AuthenticationManager.resetAuthentication()
            // Handled MQTT publish in LockStateSingleton
        },
        onFailed = { ToastManager.show(activity, "Failed to lock: $it") }
    )
}

fun tryUnlockTask(activity: Activity?): Boolean {
    if (activity == null) {
        return false
    }
    return tryLockAction(
        activity,
        action = {
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
                    e.printStackTrace()
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
        e.printStackTrace()
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
            description = "Please authenticate to unlock ${Constants.APP_NAME}"
        )
    } else {
        tryUnlockTask(activity)
        CoroutineScope(Dispatchers.Main).launch {
            WaitingForUnlockStateSingleton.emitUnlockSuccess()
        }
    }
}
