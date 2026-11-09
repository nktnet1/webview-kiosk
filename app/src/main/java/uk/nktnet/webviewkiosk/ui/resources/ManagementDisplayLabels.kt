package uk.nktnet.webviewkiosk.ui.resources

import androidx.annotation.StringRes
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.data.ManagementOwner
import uk.nktnet.webviewkiosk.config.data.ManagementUserType

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
