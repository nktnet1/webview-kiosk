package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.auth.BiometricPromptManager
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.UnlockAuthRequirementOption
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton

private fun tryLockAction(
    activity: Activity?,
    action: Activity.() -> Unit,
    showToast: (String) -> Unit = {},
    defaultMsg: String
): Boolean {
    if (activity == null) {
        showToast("Activity is not initialised.")
        return false
    }

    try {
        activity.action()
    } catch (e: SecurityException) {
        showToast("[SecurityException] $defaultMsg: ${e.message}")
        return false
    } catch (e: IllegalArgumentException) {
        showToast("[IllegalArgumentException] $defaultMsg: ${e.message}")
        return false
    } catch (e: Exception) {
        showToast("[UnknownException] $defaultMsg: ${e.message}")
        return false
    }
    return true
}

fun tryLockTask(activity: Activity?, showToast: (String) -> Unit = {}): Boolean {
    return tryLockAction(activity, Activity::startLockTask, showToast, "Failed to lock app")
}

fun tryUnlockTask(activity: Activity?, showToast: (String) -> Unit = {}): Boolean {
    return tryLockAction(activity, Activity::stopLockTask, showToast, "Failed to unlock app")
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
    if (userSettings.unlockAuthRequirement === UnlockAuthRequirementOption.OFF) {
        return false
    }
    if (userSettings.unlockAuthRequirement === UnlockAuthRequirementOption.REQUIRE) {
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
        BiometricPromptManager.showBiometricPrompt(
            title = "Authentication Required",
            description = "Please authenticate to unlock Webview Kiosk"
        )
    } else {
        tryUnlockTask(activity, showToast)
    }
}
