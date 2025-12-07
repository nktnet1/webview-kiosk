package uk.nktnet.webviewkiosk.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

const val INTENT_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK

fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = INTENT_FLAGS
    }
    context.startActivity(intent)
}

fun openDefaultLauncherSettings(context: Context) {
    val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
        flags = INTENT_FLAGS
    }
    context.startActivity(intent)
}

@RequiresApi(Build.VERSION_CODES.N)
fun openDefaultAppsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
        flags = INTENT_FLAGS
    }
    context.startActivity(intent)
}

fun openWifiSettings(context: Context) {
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
        flags = INTENT_FLAGS
    }
    context.startActivity(intent)
}

@RequiresApi(Build.VERSION_CODES.P)
fun openDataUsageSettings(context: Context) {
    val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS).apply {
        flags = INTENT_FLAGS
    }
    context.startActivity(intent)
}

fun openSettings(context: Context) {
    val intent = Intent(Settings.ACTION_SETTINGS).apply {
        flags = INTENT_FLAGS
    }
    context.startActivity(intent)
}
