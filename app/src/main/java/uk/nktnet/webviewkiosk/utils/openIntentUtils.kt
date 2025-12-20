package uk.nktnet.webviewkiosk.utils

import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import uk.nktnet.webviewkiosk.config.UserSettings
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
    lockTask: Boolean,
    activityName: String? = null,
) {
    val userSettings = UserSettings(context)
    var options: ActivityOptions? = null
    var intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK

    if (lockTask) {
        val dpm = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        if (!dpm.isLockTaskPermitted(context.packageName)) {
            ToastManager.show(
                context,
                "Error: ${context.packageName} must be lock task permitted to launch apps."
            )
            return
        }
        if (!dpm.isLockTaskPermitted(packageName)) {
            ToastManager.show(
                context,
                "Error: $packageName is not lock task permitted in settings."
            )
            return
        }
        if (!userSettings.lockTaskFeatureHome) {
            ToastManager.show(
                context,
                "Error: lock task feature HOME (under device owner) is required."
            )
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            ToastManager.show(
                context,
                "Error: kiosk launch requires Android API ${Build.VERSION_CODES.P}+ (current: ${Build.VERSION.SDK_INT})."
            )
            return
        }
        applyLockTaskFeatures(context)
        options = ActivityOptions.makeBasic().setLockTaskEnabled(true)
        intentFlags = intentFlags or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    try {
        val intent = if (activityName != null) {
            Intent().apply {
                component = ComponentName(packageName, activityName)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(packageName)
        }

        if (intent != null) {
            intent.addFlags(intentFlags)
            if (lockTask) {
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
