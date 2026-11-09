package uk.nktnet.webviewkiosk.ui.resources

import androidx.annotation.StringRes
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.config.data.ManagementOwner
import uk.nktnet.webviewkiosk.config.data.ManagementUserType

@get:StringRes
val DeviceOwnerMode.displayLabelRes: Int
    get() =
        when (this) {
            DeviceOwnerMode.None -> R.string.ui_none
            DeviceOwnerMode.DeviceOwner -> R.string.about_app_device_owner
            DeviceOwnerMode.ProfileOwner -> R.string.about_app_profile_owner
            DeviceOwnerMode.Dhizuku -> R.string.ui_dhizuku
        }

@get:StringRes
val ManagementOwner.displayLabelRes: Int
    get() =
        when (this) {
            ManagementOwner.None -> R.string.ui_management_owner_none
            ManagementOwner.DeviceOwner -> R.string.about_app_device_owner
            ManagementOwner.ProfileOwner -> R.string.about_app_profile_owner
            ManagementOwner.Unknown -> R.string.ui_management_unknown
        }

@get:StringRes
val ManagementUserType.displayLabelRes: Int
    get() =
        when (this) {
            ManagementUserType.FullUser -> R.string.ui_management_full_user
            ManagementUserType.ManagedProfile -> R.string.ui_management_work_profile
            ManagementUserType.Profile -> R.string.ui_management_profile
            ManagementUserType.Unknown -> R.string.ui_management_unknown
        }

// Presentation labels must not change the enum names used by the remote API.
@StringRes
fun managementModeLabelRes(value: String): Int =
    DeviceOwnerMode.entries.firstOrNull { it.name == value }?.displayLabelRes
        ?: R.string.ui_management_unknown

@StringRes
fun managementOwnerLabelRes(value: String): Int =
    ManagementOwner.entries.firstOrNull { it.name == value }?.displayLabelRes
        ?: R.string.ui_management_unknown

@StringRes
fun managementUserTypeLabelRes(value: String): Int =
    ManagementUserType.entries.firstOrNull { it.name == value }?.displayLabelRes
        ?: R.string.ui_management_unknown
