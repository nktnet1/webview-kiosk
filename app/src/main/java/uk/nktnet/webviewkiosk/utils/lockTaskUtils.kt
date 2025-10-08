package uk.nktnet.webviewkiosk.utils

import android.app.Activity

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
