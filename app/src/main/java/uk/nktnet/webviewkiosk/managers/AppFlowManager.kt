package uk.nktnet.webviewkiosk.managers

import android.app.admin.DeviceAdminInfo
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import uk.nktnet.webviewkiosk.config.data.AdminAppInfo
import uk.nktnet.webviewkiosk.config.data.AppInfo
import uk.nktnet.webviewkiosk.config.data.AppLoadState
import uk.nktnet.webviewkiosk.config.data.AppType
import uk.nktnet.webviewkiosk.config.data.LaunchableAppInfo
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager.DAR
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager.DPM

object AppFlowManager {
    private fun getLaunchablePackages(
        context: Context,
        filterLockTaskPermitted: Boolean = false
    ): Map<String, Pair<List<ResolveInfo>, Boolean>> {
        val pm = context.packageManager
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        return pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        ).groupBy { it.activityInfo.packageName }
            .mapValues { (pkg, list) ->
                list to dpm.isLockTaskPermitted(pkg)
            }
            .filter { !filterLockTaskPermitted || it.value.second }
    }

    private fun getAppsFlowFromPackageList(
        context: Context,
        packagesList: List<String>,
        chunkSize: Int = 5
    ): Flow<AppLoadState<AppInfo>> = flow {
        val pm = context.packageManager
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

    fun getLaunchablePackageNames(
        context: Context,
        filterLockTaskPermitted: Boolean = false
    ): List<String> {
        return getLaunchablePackages(context, filterLockTaskPermitted)
            .keys
            .toList()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getLockTaskPackageNames(context: Context): List<String> {
        if (!DeviceOwnerManager.hasOwnerPermission(context)) {
            return emptyList()
        }
        try {
            return DPM.getLockTaskPackages(DAR).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun getLaunchableAppsFlow(
        context: Context,
        chunkSize: Int = 10,
        filterLockTaskPermitted: Boolean = false,
    ): Flow<AppLoadState<LaunchableAppInfo>> = flow {
        val pm = context.packageManager
        val resolved = getLaunchablePackages(context, filterLockTaskPermitted)

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
                    admin = ComponentName(
                        deviceAdminInfo.packageName,
                        deviceAdminInfo.receiverName
                    )
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
    ): Flow<AppLoadState<AppInfo>> {
        return getAppsFlowFromPackageList(
            context,
            getLockTaskPackageNames(context),
            chunkSize
        )
    }

    fun getInstalledAppsFlow(
        context: Context,
        appType: AppType,
        chunkSize: Int = 10
    ): Flow<AppLoadState<AppInfo>> {
        val pm = context.packageManager
        val packageNames = pm.getInstalledApplications(0)
            .filter { appInfo ->
                val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                when (appType) {
                    AppType.USER_APPS -> !isSystem
                    AppType.SYSTEM_APPS -> isSystem
                    AppType.ALL_APPS -> true
                }
            }
            .map { it.packageName }

        return getAppsFlowFromPackageList(context, packageNames, chunkSize)
    }
}
