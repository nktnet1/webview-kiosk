package com.nktnet.webview_kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.nktnet.webview_kiosk.utils.setupLockTaskPackage

class WebviewKioskAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        setupLockTaskPackage(context)
        super.onEnabled(context, intent)
    }
}
