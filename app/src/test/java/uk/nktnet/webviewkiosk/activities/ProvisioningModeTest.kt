package uk.nktnet.webviewkiosk.activities

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33, 37])
class ProvisioningModeTest {
    @Test
    fun deviceOwnerProvisioningRemainsTheDefaultForOlderFlows() {
        verifyMode(Intent(), Activity.RESULT_OK)
    }

    @Test
    @Config(sdk = [33, 37])
    fun allowedDeviceOwnerModeIsChosenRatherThanWorkProfileMode() {
        verifyMode(
            allowedModes(
                DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
            ),
            Activity.RESULT_OK,
        )
    }

    @Test
    @Config(sdk = [33, 37])
    fun workProfileOnlyProvisioningIsCancelledWithoutClaimingMainUserOwnership() {
        verifyMode(
            allowedModes(DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE),
            Activity.RESULT_CANCELED,
        )
    }

    @Test
    @Config(sdk = [33, 37])
    fun emptyAllowedModeListIsRejected() {
        verifyMode(allowedModes(), Activity.RESULT_CANCELED)
    }

    private fun allowedModes(vararg modes: Int) =
        Intent()
            .putIntegerArrayListExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES,
                ArrayList(modes.toList()),
            )

    private fun verifyMode(intent: Intent, expected: Int) {
        val controller =
            Robolectric.buildActivity(ProvisioningActivity::class.java, intent)
                .create()
        try {
            val activity = controller.get()
            assertTrue(activity.isFinishing)
            val result = shadowOf(activity)
            assertEquals(expected, result.resultCode)
            if (expected == Activity.RESULT_OK) {
                assertEquals(
                    DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
                    result.resultIntent.getIntExtra(
                        DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                        -1,
                    ),
                )
            } else {
                assertFalse(
                    result.resultIntent?.hasExtra(
                        DevicePolicyManager.EXTRA_PROVISIONING_MODE
                    ) ?: false
                )
            }
        } finally {
            controller.destroy()
        }
    }
}
