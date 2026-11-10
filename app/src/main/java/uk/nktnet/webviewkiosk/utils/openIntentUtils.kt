package uk.nktnet.webviewkiosk.utils

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.services.LockTaskService

fun safeStartActivity(context: Context, intent: Intent, bundle: Bundle? = null) {
    try {
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent, bundle)
            AuthenticationManager.bypassAuthForWindow()
        } else {
            val shortName = intent.action?.substringAfterLast('.') ?: "Unknown"
            ToastManager.show(
                context,
                context.getString(
                    R.string.ui_no_activity_for_intent_value,
                    shortName,
                ),
            )
        }
    } catch (e: Exception) {
        Log.e(Constants.APP_SCHEME, "Failed to start activity", e)
        ToastManager.show(
            context,
            context.getString(R.string.ui_error_value, e.message),
        )
    }
}

fun openAppDetailsSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    safeStartActivity(context, intent)
}

fun openAppNotificationsSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        safeStartActivity(context, intent)
    } else {
        openAppDetailsSettings(context)
    }
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
    intent: Intent? = null,
) {
    val activityManager =
        context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
    val userSettings = UserSettings(context)
    var options: ActivityOptions? = null
    var intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK
    val isLocked = getIsLocked(activityManager)

    if (isLocked) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                activityManager.lockTaskModeState ==
                    ActivityManager.LOCK_TASK_MODE_PINNED
        ) {
            ToastManager.show(
                context,
                context.getString(
                    R.string.ui_cannot_launch_apps_while_screen_pinned
                ),
            )
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            ToastManager.show(
                context,
                context.getString(
                    R.string
                        .ui_error_kiosk_launch_requires_android_api_value_current,
                    Build.VERSION_CODES.P.toString(),
                    Build.VERSION.SDK_INT.toString(),
                ),
            )
            return
        }
        val dpm =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
        if (!dpm.isLockTaskPermitted(context.packageName)) {
            ToastManager.show(
                context,
                context.getString(
                    R.string
                        .ui_error_value_must_be_lock_task_permitted_to_launch_apps,
                    context.packageName,
                ),
            )
            return
        }
        if (!dpm.isLockTaskPermitted(packageName)) {
            ToastManager.show(
                context,
                context.getString(
                    R.string.ui_error_value_is_not_lock_task_permitted_in_settings,
                    packageName,
                ),
            )
            return
        }
        if (
            DeviceOwnerManager.getCapabilities(context).lockTaskFeatures &&
                !userSettings.lockTaskFeatureHome
        ) {
            ToastManager.show(
                context,
                context.getString(
                    R.string
                        .ui_error_lock_task_feature_home_under_device_owner_is_req
                ),
            )
            return
        }
        applyLockTaskFeatures(context)
        options = ActivityOptions.makeBasic().setLockTaskEnabled(true)
        intentFlags = intentFlags or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    try {
        val launchIntent =
            intent
                ?: if (activityName != null) {
                    Intent().apply {
                        component = ComponentName(packageName, activityName)
                    }
                } else {
                    context.packageManager.getLaunchIntentForPackage(packageName)
                }

        if (launchIntent != null) {
            if (isLocked) {
                context.startForegroundService(
                    Intent(
                        context,
                        LockTaskService::class.java,
                    )
                )
            }
            launchIntent.addFlags(intentFlags)
            safeStartActivity(
                context,
                launchIntent,
                options?.toBundle(),
            )
        } else {
            ToastManager.show(
                context,
                context.getString(
                    R.string.ui_error_value_cannot_be_opened,
                    packageName,
                ),
            )
        }
    } catch (e: Exception) {
        ToastManager.show(context, context.getString(R.string.ui_error_value, e))
    }
}

fun handleExternalSchemeUrl(context: Context, url: String) {
    try {
        val uri = url.toUri()
        val intent =
            if (uri.scheme == "intent") {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, url.toUri())
            }

        val resolveInfo =
            context.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        val packageName = resolveInfo?.activityInfo?.packageName

        if (packageName == null) {
            ToastManager.show(
                context,
                context.getString(
                    R.string.ui_error_handling_intent_no_package_available_for_value,
                    url,
                ),
            )
            return
        }

        openPackage(
            context = context,
            packageName = packageName,
            activityName = resolveInfo.activityInfo.name,
            intent = intent,
        )
    } catch (e: Exception) {
        Log.e(Constants.APP_SCHEME, "Failed to handle intent URL scheme", e)
    }
}

fun isPackageInstalled(context: Context, packageName: String): Boolean {
    try {
        context.packageManager.getPackageInfo(packageName, 0)
        return true
    } catch (_: PackageManager.NameNotFoundException) {
        return false
    }
}
