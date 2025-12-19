package uk.nktnet.webviewkiosk.utils

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import uk.nktnet.webviewkiosk.services.LockTaskService
import uk.nktnet.webviewkiosk.managers.ToastManager

fun safeStartActivity(context: Context, intent: Intent, bundle: Bundle? = null) {
    try {
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent, bundle)
        } else {
            val shortName = intent.action?.substringAfterLast('.') ?: "Unknown"
            ToastManager.show(
                context,
                "No activity for intent: $shortName"
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ToastManager.show(
            context,
            "Error: ${e.message}"
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

fun openPackage(
    context: Context,
    packageName: String,
    activityName: String? = null,
    lockTask: Boolean = true,
) {
    try {
        val intent = if (activityName != null) {
            Intent().apply {
                component = ComponentName(packageName, activityName)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(packageName)
        }

        if (intent != null) {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                or if (lockTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0
            )
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lockTask) {
                ActivityOptions.makeBasic().setLockTaskEnabled(true)
            } else {
                null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lockTask) {
                context.startForegroundService(
                    Intent(
                        context,
                        LockTaskService::class.java
                    )
                )
            }
            safeStartActivity(
                context,
                intent,
                options?.toBundle()
            )
        } else {
            ToastManager.show(context, "Error: $packageName cannot be opened.")
        }
    } catch (e: Exception) {
        ToastManager.show(context, "Error: $e")
    }
}
