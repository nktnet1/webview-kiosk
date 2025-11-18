package uk.nktnet.webviewkiosk.main

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.app.admin.IDevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import org.lsposed.hiddenapibypass.HiddenApiBypass
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuBinderWrapper
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener

object DhizukuManager {
    private var isInit = false

    fun init(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }

        isInit = try {
            val setHiddenSuccess = HiddenApiBypass.setHiddenApiExemptions("")
            if (!setHiddenSuccess) {
                return false
            }
            Dhizuku.init(context)
        } catch (_: Throwable) {
            false
        }

        return isInit
    }

    @SuppressLint("PrivateApi")
    fun binderWrapperDevicePolicyManager(appContext: Context): DevicePolicyManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null
        }
        try {
            val context = appContext.createPackageContext(
                Dhizuku.getOwnerComponent().packageName,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val field = manager.javaClass.getDeclaredField("mService")
            field.isAccessible = true
            val oldInterface = field[manager] as IDevicePolicyManager
            if (oldInterface is DhizukuBinderWrapper) return manager
            val oldBinder = oldInterface.asBinder()
            val newBinder = Dhizuku.binderWrapper(oldBinder)
            val newInterface = IDevicePolicyManager.Stub.asInterface(newBinder)
            field[manager] = newInterface
            return manager
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun requestPermission(onGranted: () -> Unit = {}, onDenied: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
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
        } catch (e: Throwable) {
            e.printStackTrace()
            onDenied()
        }
    }

    fun setupLockTaskPackage(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }
        try {
            val dpm = binderWrapperDevicePolicyManager(context)
            if (dpm == null) {
                return false
            }
            dpm.setLockTaskPackages(
                Dhizuku.getOwnerComponent(),
                arrayOf(context.packageName)
            )
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
