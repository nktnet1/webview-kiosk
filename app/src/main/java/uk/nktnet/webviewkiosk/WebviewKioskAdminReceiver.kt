package uk.nktnet.webviewkiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage

class WebviewKioskAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Device Admin: Enabled", Toast.LENGTH_SHORT).show()
        setupLockTaskPackage(context)
        super.onEnabled(context, intent)
    }
}
