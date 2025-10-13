package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver

private fun tryLockAction(
    activity: Activity?,
    action: Activity.() -> Unit,
    showToast: (String) -> Unit = {},
    defaultMsg: String
) {
    if (activity == null) {
        showToast("Activity is not initialised.")
        return
    }

    try {
        activity.action()
    } catch (e: SecurityException) {
        showToast("[SecurityException] $defaultMsg: ${e.message}")
    } catch (e: IllegalArgumentException) {
        showToast("[IllegalArgumentException] $defaultMsg: ${e.message}")
    } catch (e: Exception) {
        showToast("[UnknownException] $defaultMsg: ${e.message}")
    }
}

fun tryLockTask(activity: Activity?, showToast: (String) -> Unit = {}) {
    tryLockAction(activity, Activity::startLockTask, showToast, "Failed to lock app")
}

fun tryUnlockTask(activity: Activity?, showToast: (String) -> Unit = {}) {
    tryLockAction(activity, Activity::stopLockTask, showToast, "Failed to unlock app")
}

fun setupDeviceOwner(context: Context): Boolean {
    try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val appPackage = context.packageName
        val receiverName = WebviewKioskAdminReceiver::class.java.name
        val adminComponent = ComponentName(appPackage, receiverName)
        dpm.setLockTaskPackages(adminComponent, arrayOf(appPackage))
        return true
    } catch (_: Exception) {
        return false
    }
}
