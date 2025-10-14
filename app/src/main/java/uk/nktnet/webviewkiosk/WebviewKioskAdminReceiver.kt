package uk.nktnet.webviewkiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage

class WebviewKioskAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        setupLockTaskPackage(context)
        super.onEnabled(context, intent)
    }
}
