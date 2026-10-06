package uk.nktnet.webviewkiosk.managers

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.app.admin.IDevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuBinderWrapper
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import kotlin.time.Duration.Companion.milliseconds

object DeviceOwnerManager {
    private const val DHIZUKU_SETTLE_TIME_MS = 1000L
    private val DHIZUKU_RETRY_DELAYS_MS = longArrayOf(100L, 250L, 650L, 1000L, 1000L)

    lateinit var DPM: DevicePolicyManager
        private set
    lateinit var DAR: ComponentName
        private set
    private var dhizukuBinderAvailableSince = 0L

    data class Status(
        var mode: DeviceOwnerMode = DeviceOwnerMode.None,
    )

    val status = MutableStateFlow(Status())

    @Synchronized
    fun init(context: Context) {
        resetToPlatformDpm(context)

        if (DPM.isDeviceOwnerApp(context.packageName)) {
            dhizukuBinderAvailableSince = 0L
            updateStatus(DeviceOwnerMode.DeviceOwner)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }

        try {
            if (!HiddenApiBypass.setHiddenApiExemptions("") || !Dhizuku.init(context)) {
                dhizukuBinderAvailableSince = 0L
                return
            }

            val now = SystemClock.elapsedRealtime()
            if (dhizukuBinderAvailableSince == 0L) {
                dhizukuBinderAvailableSince = now
                return
            }
            if (now - dhizukuBinderAvailableSince < DHIZUKU_SETTLE_TIME_MS) {
                return
            }

            val dpm = binderWrapperDevicePolicyManager(context) ?: return
            val dar = Dhizuku.getOwnerComponent()

            DPM = dpm
            DAR = dar
            updateStatus(DeviceOwnerMode.Dhizuku)
        } catch (e: Throwable) {
            invalidateDhizuku(context)
            Log.w(javaClass.simpleName, "Dhizuku is not ready", e)
        }
    }

    suspend fun initWithDhizukuRetry(context: Context): DeviceOwnerMode {
        init(context)
        if (
            status.value.mode != DeviceOwnerMode.None
            || Build.VERSION.SDK_INT < Build.VERSION_CODES.P
        ) {
            return status.value.mode
        }

        for (retryDelay in DHIZUKU_RETRY_DELAYS_MS) {
            delay(retryDelay.milliseconds)
            init(context)
            if (status.value.mode != DeviceOwnerMode.None) {
                break
            }
        }

        return status.value.mode
    }

    private fun resetToPlatformDpm(context: Context) {
        DPM = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        DAR = ComponentName(
            context.packageName,
            WebviewKioskAdminReceiver::class.java.name
        )
        updateStatus(DeviceOwnerMode.None)
    }

    private fun invalidateDhizuku(context: Context) {
        dhizukuBinderAvailableSince = 0L
        resetToPlatformDpm(context)
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
            if (status.value.mode == DeviceOwnerMode.Dhizuku) {
                invalidateDhizuku(context)
            }
            Log.w(javaClass.simpleName, "Failed to check owner permission", e)
            false
        }
    }

    fun requestDhizukuPermission(
        context: Context,
        onGranted: () -> Unit = {},
        onDenied: () -> Unit = {},
    ) {
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
            if (status.value.mode == DeviceOwnerMode.Dhizuku) {
                // The cached binder/wrapper is no longer trustworthy after a remote failure.
                invalidateDhizuku(context)
            }
            Log.e(javaClass.simpleName, "Failed to request Dhizuku permission", e)
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
            Log.e(
                javaClass.simpleName,
                "Failed to create Dhizuku binder wrapper",
                e
            )
        }
        return null
    }
}
