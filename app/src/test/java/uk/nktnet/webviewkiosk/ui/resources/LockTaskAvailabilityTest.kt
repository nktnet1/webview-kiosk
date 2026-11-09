package uk.nktnet.webviewkiosk.ui.resources

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
                26 to R.string.ui_lock_task_requires_android_8,
                28 to R.string.ui_lock_task_requires_android_9,
                30 to R.string.ui_lock_task_requires_android_11,
            )) {
            assertEquals(
                message,
                lockTaskPolicyUnavailableReason(
                    minimum - 1,
                    minimum,
                    nativeOwner,
                    false,
                    false,
                ),
            )
        }
    }

    @Test
    fun advancedFeatureIsUnavailableOnAndroid9And10EvenForADeviceOwner() {
        for (sdk in listOf(28, 29)) {
            assertEquals(
                R.string.ui_lock_task_requires_android_11,
                lockTaskPolicyUnavailableReason(
                    sdk,
                    30,
                    nativeOwner.copy(
                        mode = DeviceOwnerMode.DeviceOwner,
                        owner = ManagementOwner.DeviceOwner,
                    ),
                    true,
                    true,
                ),
            )
        }
        assertNull(lockTaskPolicyUnavailableReason(30, 30, nativeOwner, true, true))
    }

    @Test
    fun missingNativeAndDhizukuPermissionsHaveDifferentExplanations() {
        assertEquals(
            R.string.ui_lock_task_owner_access_required,
            lockTaskPolicyUnavailableReason(
                37,
                28,
                DeviceOwnerManager.Status(),
                false,
                false,
            ),
        )
        assertEquals(
            R.string.ui_lock_task_dhizuku_permission_required,
            lockTaskPolicyUnavailableReason(
                37,
                28,
                nativeOwner.copy(
                    mode = DeviceOwnerMode.Dhizuku,
                    owner = ManagementOwner.Unknown,
                ),
                false,
                false,
            ),
        )
    }

    @Test
    fun deniedWorkProfileAccessIsExplainedForBothNativeAndSharedOwners() {
        for (mode in listOf(DeviceOwnerMode.ProfileOwner, DeviceOwnerMode.Dhizuku)) {
            assertEquals(
                R.string.ui_lock_task_work_profile_unavailable,
                lockTaskPolicyUnavailableReason(
                    37,
                    28,
                    nativeOwner.copy(
                        mode = mode,
                        userType = ManagementUserType.ManagedProfile,
                    ),
                    true,
                    false,
                ),
            )
        }
    }

    @Test
    fun explicitlyGrantedProfilePolicyAccessIsNotDisabledByItsScope() {
        for (scope in ManagementUserType.entries) {
            assertNull(
                lockTaskPolicyUnavailableReason(
                    37,
                    28,
                    nativeOwner.copy(userType = scope),
                    true,
                    true,
                )
            )
        }
    }

    @Test
    fun deniedFullUserAccessDoesNotAssumeProfileOwnerAuthority() {
        assertEquals(
            R.string.ui_lock_task_policy_access_denied,
            lockTaskPolicyUnavailableReason(37, 28, nativeOwner, true, false),
        )
        assertEquals(
            R.string.ui_lock_task_profile_unavailable,
            lockTaskPolicyUnavailableReason(
                37,
                28,
                nativeOwner.copy(userType = ManagementUserType.Profile),
                true,
                false,
            ),
        )
    }

    @Test
    fun revokedPermissionOverridesPreviouslyGrantedPolicyCapabilities() {
        assertEquals(
            R.string.ui_lock_task_dhizuku_permission_required,
            lockTaskPolicyUnavailableReason(
                37,
                28,
                nativeOwner.copy(mode = DeviceOwnerMode.Dhizuku),
                false,
                true,
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
