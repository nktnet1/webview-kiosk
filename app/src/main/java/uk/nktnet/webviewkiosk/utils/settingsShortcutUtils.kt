package uk.nktnet.webviewkiosk.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import uk.nktnet.webviewkiosk.managers.ToastManager

private fun safeStartActivity(context: Context, intent: Intent) {
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        val shortName = intent.action?.substringAfterLast('.') ?: "Unknown"
        ToastManager.show(
            context,
            "No activity for intent: $shortName"
        )
    }
}

fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    safeStartActivity(context, intent)
}

fun openDefaultLauncherSettings(context: Context) {
    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
    safeStartActivity(context, intent)
}

@RequiresApi(Build.VERSION_CODES.N)
fun openDefaultAppsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    safeStartActivity(context, intent)
}

fun openWifiSettings(context: Context) {
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
    safeStartActivity(context, intent)
}

@RequiresApi(Build.VERSION_CODES.P)
fun openDataUsageSettings(context: Context) {
    val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
    safeStartActivity(context, intent)
}

fun openSettings(context: Context) {
    val intent = Intent(Settings.ACTION_SETTINGS)
    safeStartActivity(context, intent)
}
