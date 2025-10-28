package uk.nktnet.webviewkiosk.main

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener

object DhizukuManager {
    private var initialized = false
    private var permissionGranted = false

    fun initialize(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        initialized = try {
            Dhizuku.init(context)
        } catch (_: Throwable) {
            false
        }

        if (initialized) {
            permissionGranted = try {
                Dhizuku.isPermissionGranted()
            } catch (_: Throwable) {
                false
            }
        }

        return initialized
    }

    fun requestPermission(onGranted: () -> Unit = {}, onDenied: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onDenied()
            return
        }

        if (!initialized) {
            onDenied()
            return
        }

        try {
            if (Dhizuku.isPermissionGranted()) {
                permissionGranted = true
                onGranted()
                return
            }

            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                @Throws(RemoteException::class)
                override fun onRequestPermission(grantResult: Int) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        permissionGranted = true
                        onGranted()
                    } else {
                        permissionGranted = false
                        onDenied()
                    }
                }
            })
        } catch (_: Throwable) {
            onDenied()
        }
    }
}
