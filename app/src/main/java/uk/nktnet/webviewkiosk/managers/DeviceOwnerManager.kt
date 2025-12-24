package com.nktnet.webview_kiosk.managers

import android.annotation.SuppressLint
import android.app.admin.DeviceAdminInfo
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.admin.IDevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import androidx.annotation.RequiresApi
import org.lsposed.hiddenapibypass.HiddenApiBypass
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuBinderWrapper
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.nktnet.webview_kiosk.WebviewKioskAdminReceiver
import com.nktnet.webview_kiosk.config.data.AdminAppInfo
import com.nktnet.webview_kiosk.config.data.AppInfo
import com.nktnet.webview_kiosk.config.data.AppLoadState
import com.nktnet.webview_kiosk.config.data.DeviceOwnerMode
import com.nktnet.webview_kiosk.config.data.LaunchableAppInfo

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

    fun getLaunchableAppsFlow(
        context: Context,
        chunkSize: Int = 10,
        filterLockTaskPermitted: Boolean = false,
    ): Flow<AppLoadState<LaunchableAppInfo>> = flow {
        val pm = context.packageManager
        val dpm = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager

        val resolved = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        ).groupBy { it.activityInfo.packageName }
            .mapValues { (pkg, list) ->
                list to dpm.isLockTaskPermitted(pkg)
            }
            .filter { !filterLockTaskPermitted || it.value.second }

        if (resolved.isEmpty()) {
            emit(AppLoadState<LaunchableAppInfo>(emptyList(), 1f))
            return@flow
        }

        val total = resolved.size
        var processed = 0

        val current = mutableListOf<LaunchableAppInfo>()

        for ((pkg, pair) in resolved) {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            val (list, lockTaskPermitted) = pair
            current.add(
                LaunchableAppInfo(
                    packageName = pkg,
                    name = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo),
                    activities = list.map {
                        LaunchableAppInfo.Activity(
                            label = it.loadLabel(pm).toString(),
                            name = it.activityInfo.name
                        )
                    },
                    isLockTaskPermitted = lockTaskPermitted
                )
            )

            processed++

            if (current.size == chunkSize || processed == total) {
                emit(
                    AppLoadState(
                        apps = current.toList(),
                        progress = processed.toFloat() / total
                    )
                )
                current.clear()
            }
        }
    }.flowOn(Dispatchers.IO)

    fun getDeviceAdminReceiversFlow(
        context: Context,
        chunkSize: Int = 5
    ): Flow<AppLoadState<AdminAppInfo>> = flow {
        val pm = context.packageManager

        val filteredReceivers = pm.queryBroadcastReceivers(
            Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
            PackageManager.GET_META_DATA
        ).mapNotNull {
            try {
                DeviceAdminInfo(context, it)
            } catch (_: Exception) {
                null
            }
        }.filter {
            it.isVisible
            && it.packageName != context.packageName
            && it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
        }.distinctBy {
            it.receiverName
        }

        val total = filteredReceivers.size
        if (total == 0) {
            emit(AppLoadState<AdminAppInfo>(emptyList(), 1f))
            return@flow
        }

        val currentChunk = mutableListOf<AdminAppInfo>()

        filteredReceivers.forEachIndexed { index, deviceAdminInfo ->
            val appInfo = pm.getApplicationInfo(deviceAdminInfo.packageName, 0)
            currentChunk.add(
                AdminAppInfo(
                    packageName = appInfo.packageName,
                    name = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo),
                    admin = ComponentName(deviceAdminInfo.packageName, deviceAdminInfo.receiverName)
                )
            )

            if (currentChunk.size == chunkSize || index == total - 1) {
                emit(
                    AppLoadState(
                        currentChunk.toList(),
                        (index + 1).toFloat() / total
                    )
                )
                currentChunk.clear()
            }
        }
    }.flowOn(Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    fun getLockTaskAppsFlow(
        context: Context,
        chunkSize: Int = 5
    ): Flow<AppLoadState<AppInfo>> = flow {
        val pm = context.packageManager
        val packagesList = try {
            DPM.getLockTaskPackages(DAR)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyArray()
        }
        val total = packagesList.size
        if (total == 0) {
            emit(AppLoadState(emptyList(), 1f))
            return@flow
        }

        val currentChunk = mutableListOf<AppInfo>()

        packagesList.forEachIndexed { index, pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)

                currentChunk.add(
                    AppInfo(
                        packageName = pkg,
                        name = label,
                        icon = icon
                    )
                )
            } catch (_: Exception) {
                // skip invalid packages
            }

            if (currentChunk.size == chunkSize || index == total - 1) {
                emit(
                    AppLoadState(
                        currentChunk.toList(),
                        (index + 1).toFloat() / total
                    )
                )
                currentChunk.clear()
            }
        }
    }.flowOn(Dispatchers.IO)

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
