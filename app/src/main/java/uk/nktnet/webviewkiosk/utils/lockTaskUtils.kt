package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
class LockStateViewModel(application: Application) : AndroidViewModel(application) {
    private val _isLocked = mutableStateOf(false)
    val isLocked: State<Boolean> = _isLocked

    init {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        viewModelScope.launch {
            while (true) {
                _isLocked.value = getIsLocked(activityManager)
                delay(1000L)
            }
        }
    }
}
