package uk.nktnet.webviewkiosk.main

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener


object DhizukuManager {
    private var isInit = false

    fun init(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        isInit = try {
            Dhizuku.init(context)
        } catch (_: Throwable) {
            false
        }

        return isInit
    }

    fun requestPermission(onGranted: () -> Unit = {}, onDenied: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onDenied()
            return
        }

        if (!isInit) {
            onDenied()
            return
        }

        try {
            if (Dhizuku.isPermissionGranted()) {
                onGranted()
                return
            }

            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                @Throws(RemoteException::class)
                override fun onRequestPermission(grantResult: Int) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        onGranted()
                    } else {
                        onDenied()
                    }
                }
            })
        } catch (_: Throwable) {
            onDenied()
        }
    }
}
