package uk.nktnet.webviewkiosk.managers

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.app.admin.IDevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import org.lsposed.hiddenapibypass.HiddenApiBypass
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuBinderWrapper
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import kotlinx.coroutines.flow.MutableStateFlow
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.DeviceOwnerMode

object DeviceOwnerManager {
    lateinit var DPM: DevicePolicyManager
        private set
    lateinit var DAR: ComponentName
        private set

    data class Status(
        var mode: DeviceOwnerMode = DeviceOwnerMode.None,
    )

    val status = MutableStateFlow(Status())

    fun init(context: Context) {
        DPM = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        DAR = ComponentName(
            context.packageName,
            WebviewKioskAdminReceiver::class.java.name
        )
        if (DPM.isDeviceOwnerApp(context.packageName)) {
            updateStatus(DeviceOwnerMode.DeviceOwner)
            return
        }

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return
            }
            val success = (
                HiddenApiBypass.setHiddenApiExemptions("")
                && Dhizuku.init(context)
            )
            if (!success) {
                return
            }

            val dpm = binderWrapperDevicePolicyManager(context) ?: return

            DPM = dpm
            DAR = Dhizuku.getOwnerComponent()
            updateStatus(DeviceOwnerMode.Dhizuku)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun hasOwnerPermission(context: Context): Boolean {
        return try {
            when (status.value.mode) {
                DeviceOwnerMode.DeviceOwner -> {
                    DPM.isDeviceOwnerApp(context.packageName)
                }
                DeviceOwnerMode.Dhizuku -> {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Dhizuku.isPermissionGranted()
                } else -> {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun requestDhizukuPermission(onGranted: () -> Unit = {}, onDenied: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onDenied()
            return
        }

        if (status.value.mode != DeviceOwnerMode.Dhizuku) {
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

    private fun updateStatus(mode: DeviceOwnerMode) {
        status.value = Status(mode)
    }

    @SuppressLint("PrivateApi")
    private fun binderWrapperDevicePolicyManager(appContext: Context): DevicePolicyManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null
        }
        try {
            val context = appContext.createPackageContext(
                Dhizuku.getOwnerComponent().packageName,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val manager = context.getSystemService(
                Context.DEVICE_POLICY_SERVICE
            ) as DevicePolicyManager
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
}
