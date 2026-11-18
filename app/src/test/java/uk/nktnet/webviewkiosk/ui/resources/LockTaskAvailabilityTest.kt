package uk.nktnet.webviewkiosk.ui.resources

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.config.data.ManagementOwner
import uk.nktnet.webviewkiosk.config.data.ManagementUserType
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager

class LockTaskAvailabilityTest {
    private val nativeOwner =
        DeviceOwnerManager.Status(
            DeviceOwnerMode.ProfileOwner,
            ManagementOwner.ProfileOwner,
            ManagementUserType.FullUser,
        )

    @Test
    fun unsupportedVersionsExplainTheMinimumVersionEvenWithoutOwnerAccess() {
        for ((minimum, message) in
            listOf(
                Build.VERSION_CODES.O to R.string.ui_lock_task_requires_android_8,
                Build.VERSION_CODES.P to R.string.ui_lock_task_requires_android_9,
                Build.VERSION_CODES.R to R.string.ui_lock_task_requires_android_11,
            )) {
            assertEquals(
                message,
                lockTaskPolicyUnavailableReason(
                    minimum - 1,
                    minimum,
                    nativeOwner,
                    hasOwnerPermission = false,
                    policyAccess = false,
                ),
            )
        }
    }

    @Test
    fun advancedFeatureIsUnavailableOnAndroid9And10EvenForADeviceOwner() {
        for (sdk in listOf(Build.VERSION_CODES.P, Build.VERSION_CODES.Q)) {
            assertEquals(
                R.string.ui_lock_task_requires_android_11,
                lockTaskPolicyUnavailableReason(
                    sdk,
                    Build.VERSION_CODES.R,
                    nativeOwner.copy(
                        mode = DeviceOwnerMode.DeviceOwner,
                        owner = ManagementOwner.DeviceOwner,
                    ),
                    hasOwnerPermission = true,
                    policyAccess = true,
                ),
            )
        }
        assertNull(
            lockTaskPolicyUnavailableReason(
                Build.VERSION_CODES.R,
                Build.VERSION_CODES.R,
                nativeOwner,
                hasOwnerPermission = true,
                policyAccess = true,
            )
        )
    }

    @Test
    fun missingNativeAndDhizukuPermissionsHaveDifferentExplanations() {
        assertEquals(
            R.string.ui_lock_task_owner_access_required,
            lockTaskPolicyUnavailableReason(
                Build.VERSION_CODES.CINNAMON_BUN,
                Build.VERSION_CODES.P,
                DeviceOwnerManager.Status(),
                hasOwnerPermission = false,
                policyAccess = false,
            ),
        )
        assertEquals(
            R.string.ui_lock_task_dhizuku_permission_required,
            lockTaskPolicyUnavailableReason(
                Build.VERSION_CODES.CINNAMON_BUN,
                Build.VERSION_CODES.P,
                nativeOwner.copy(
                    mode = DeviceOwnerMode.Dhizuku,
                    owner = ManagementOwner.Unknown,
                ),
                hasOwnerPermission = false,
                policyAccess = false,
            ),
        )
    }

    @Test
    fun deniedWorkProfileAccessIsExplainedForBothNativeAndSharedOwners() {
        for (mode in listOf(DeviceOwnerMode.ProfileOwner, DeviceOwnerMode.Dhizuku)) {
            assertEquals(
                R.string.ui_lock_task_work_profile_unavailable,
                lockTaskPolicyUnavailableReason(
                    Build.VERSION_CODES.CINNAMON_BUN,
                    Build.VERSION_CODES.P,
                    nativeOwner.copy(
                        mode = mode,
                        userType = ManagementUserType.ManagedProfile,
                    ),
                    hasOwnerPermission = true,
                    policyAccess = false,
                ),
            )
        }
    }

    @Test
    fun explicitlyGrantedProfilePolicyAccessIsNotDisabledByItsScope() {
        for (scope in ManagementUserType.entries) {
            assertNull(
                lockTaskPolicyUnavailableReason(
                    Build.VERSION_CODES.CINNAMON_BUN,
                    Build.VERSION_CODES.P,
                    nativeOwner.copy(userType = scope),
                    hasOwnerPermission = true,
                    policyAccess = true,
                )
            )
        }
    }

    @Test
    fun deniedFullUserAccessDoesNotAssumeProfileOwnerAuthority() {
        assertEquals(
            R.string.ui_lock_task_policy_access_denied,
            lockTaskPolicyUnavailableReason(
                Build.VERSION_CODES.CINNAMON_BUN,
                Build.VERSION_CODES.P,
                nativeOwner,
                hasOwnerPermission = true,
                policyAccess = false,
            ),
        )
        assertEquals(
            R.string.ui_lock_task_profile_unavailable,
            lockTaskPolicyUnavailableReason(
                Build.VERSION_CODES.CINNAMON_BUN,
                Build.VERSION_CODES.P,
                nativeOwner.copy(userType = ManagementUserType.Profile),
                hasOwnerPermission = true,
                policyAccess = false,
            ),
        )
    }

    @Test
    fun revokedPermissionOverridesPreviouslyGrantedPolicyCapabilities() {
        assertEquals(
            R.string.ui_lock_task_dhizuku_permission_required,
            lockTaskPolicyUnavailableReason(
                Build.VERSION_CODES.CINNAMON_BUN,
                Build.VERSION_CODES.P,
                nativeOwner.copy(mode = DeviceOwnerMode.Dhizuku),
                hasOwnerPermission = false,
                policyAccess = true,
            ),
        )
    }

    @Test
    fun displayLabelsCoverNativeAndSharedOwnersWithoutChangingWireNames() {
        assertEquals(
            R.string.about_app_profile_owner,
            managementModeLabelRes("ProfileOwner"),
        )
        assertEquals(
            R.string.about_app_device_owner,
            managementModeLabelRes("DeviceOwner"),
        )
        assertEquals(R.string.ui_dhizuku, managementModeLabelRes("Dhizuku"))
        assertEquals(R.string.ui_none, managementModeLabelRes("None"))
        assertEquals(
            R.string.about_app_profile_owner,
            managementOwnerLabelRes("ProfileOwner"),
        )
        assertEquals(
            R.string.ui_management_full_user,
            managementUserTypeLabelRes("FullUser"),
        )
        assertEquals(
            R.string.ui_management_work_profile,
            managementUserTypeLabelRes("ManagedProfile"),
        )
        assertEquals(
            R.string.ui_management_unknown,
            managementModeLabelRes("future-mode"),
        )
        assertEquals(
            R.string.ui_management_unknown,
            managementOwnerLabelRes("future-owner"),
        )
        assertEquals(
            R.string.ui_management_unknown,
            managementUserTypeLabelRes("future-scope"),
        )
    }
}
