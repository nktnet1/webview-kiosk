package uk.nktnet.webviewkiosk.activities

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi

class ProvisioningActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val allowedModes =
                intent.getIntegerArrayListExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES
                )
            if (
                allowedModes != null &&
                    DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE !in
                        allowedModes
            ) {
                // Managed-profile enrollment is a separate work profile, never a
                // way to assign a Profile Owner to the existing full user.
                setResult(RESULT_CANCELED)
                finish()
                return
            }
        }
        val intent = Intent()
        intent.putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_MODE,
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
        )
        setResult(RESULT_OK, intent)
        finish()
    }
}
