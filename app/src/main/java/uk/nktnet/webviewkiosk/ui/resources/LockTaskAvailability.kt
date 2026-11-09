package uk.nktnet.webviewkiosk.ui.resources

import android.os.Build
import androidx.annotation.StringRes
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.config.data.ManagementUserType
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager

/** Explains availability independently of managed app restrictions. */
@StringRes
fun lockTaskPolicyUnavailableReason(
    sdkInt: Int,
    minimumSdk: Int,
    status: DeviceOwnerManager.Status,
    hasOwnerPermission: Boolean,
    policyAccess: Boolean,
): Int? =
    when {
        sdkInt < minimumSdk ->
            when (minimumSdk) {
                Build.VERSION_CODES.O -> R.string.ui_lock_task_requires_android_8
                Build.VERSION_CODES.P -> R.string.ui_lock_task_requires_android_9
                Build.VERSION_CODES.R -> R.string.ui_lock_task_requires_android_11
                else -> error("No Lock Task availability label for API $minimumSdk")
            }
        !hasOwnerPermission && status.mode == DeviceOwnerMode.Dhizuku ->
            R.string.ui_lock_task_dhizuku_permission_required
        !hasOwnerPermission -> R.string.ui_lock_task_owner_access_required
        policyAccess -> null
        status.userType == ManagementUserType.ManagedProfile ->
            R.string.ui_lock_task_work_profile_unavailable
        status.userType == ManagementUserType.Profile ->
            R.string.ui_lock_task_profile_unavailable
        else -> R.string.ui_lock_task_policy_access_denied
    }
