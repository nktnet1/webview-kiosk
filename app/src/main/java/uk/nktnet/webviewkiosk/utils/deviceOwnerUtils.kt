package uk.nktnet.webviewkiosk.utils

import android.app.admin.DeviceAdminInfo
import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class DeviceAdmin(
    val app: DeviceAdminAppInfo,
    val admin: ComponentName
)

data class DeviceAdminAppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable
)

fun getDeviceAdminReceivers(context: Context, pm: PackageManager): List<DeviceAdmin> {
    return pm.queryBroadcastReceivers(
        Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
        PackageManager.GET_META_DATA
    ).mapNotNull {
        try {
            DeviceAdminInfo(context, it)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }.filter {
        it.isVisible
        && it.packageName != context.packageName
        && it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }.map { deviceAdminInfo ->
        val appInfo = pm.getApplicationInfo(deviceAdminInfo.packageName, 0)
        val app = DeviceAdminAppInfo(
            packageName = appInfo.packageName,
            name = pm.getApplicationLabel(appInfo).toString(),
            icon = pm.getApplicationIcon(appInfo)
        )
        val componentName = ComponentName(
            deviceAdminInfo.packageName,
            deviceAdminInfo.receiverName
        )
        DeviceAdmin(app, componentName)
    }
}
