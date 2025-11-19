package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.auth.AuthenticationManager
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.UnlockAuthRequirementOption
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton

private fun tryLockAction(
    activity: Activity?,
    action: Activity.() -> Unit,
    onSuccess: () -> Unit = {},
    onFailed: (String) -> Unit
): Boolean {
    if (activity == null) {
        onFailed("Activity is not initialised.")
        return false
    }

    return try {
        activity.action()
        onSuccess()
        true
    } catch (e: SecurityException) {
        onFailed("[SecurityException] ${e.message}")
        false
    } catch (e: IllegalArgumentException) {
        onFailed("[IllegalArgumentException] ${e.message}")
        false
    } catch (e: Exception) {
        onFailed("[UnknownException] ${e.message}")
        false
    }
}

fun applyLockTaskFeatures(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        return
    }

    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    if (!dpm.isDeviceOwnerApp(context.packageName)) {
        return
    }

    val userSettings = UserSettings(context)
    var features = DevicePolicyManager.LOCK_TASK_FEATURE_NONE

    if (userSettings.lockTaskFeatureHome) {
        features = features or DevicePolicyManager.LOCK_TASK_FEATURE_HOME
    }
    if (userSettings.lockTaskFeatureOverview) {
        features = features or DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
    }
    if (userSettings.lockTaskFeatureGlobalActions) {
        features = features or DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
    }
    if (userSettings.lockTaskFeatureNotifications) {
        features = features or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
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
    val adminComponent = ComponentName(
        context.packageName,
        WebviewKioskAdminReceiver::class.java.name
    )
    try {
        dpm.setLockTaskFeatures(adminComponent, features)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun tryLockTask(activity: Activity?, showToast: (String) -> Unit = {}): Boolean {
    activity?.let {
        applyLockTaskFeatures(activity)
    }
    return tryLockAction(
        activity,
        Activity::startLockTask,
        onSuccess = {
            AuthenticationManager.resetAuthentication()
            // Handled MQTT publish in LockStateSingleton
        },
        onFailed = { showToast("Failed to lock: $it") }
    )
}

fun tryUnlockTask(activity: Activity?, showToast: (String) -> Unit = {}): Boolean {
    return tryLockAction(
        activity,
        Activity::stopLockTask,
        onSuccess = {
            // Handled MQTT publish in LockStateSingleton
        },
        onFailed = { showToast("Failed to unlock: $it") }
    )
}

fun setupLockTaskPackage(context: Context): Boolean {
    try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            return false
        }
        val adminComponent = ComponentName(context.packageName, WebviewKioskAdminReceiver::class.java.name)
        dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
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

fun unlockWithAuthIfRequired(
    activity: Activity,
    showToast: (String) -> Unit
) {
    val userSettings = UserSettings(activity)

    if (requireAuthForUnlock(activity, userSettings)) {
        WaitingForUnlockStateSingleton.startWaiting()
        AuthenticationManager.showAuthenticationPrompt(
            title = "Authentication Required",
            description = "Please authenticate to unlock Webview Kiosk"
        )
    } else {
        tryUnlockTask(activity, showToast)
        CoroutineScope(Dispatchers.Main).launch {
            WaitingForUnlockStateSingleton.emitUnlockSuccess()
        }
    }
}
