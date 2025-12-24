package com.nktnet.webview_kiosk.ui.components.setting.dialog

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.data.LaunchableAppInfo
import com.nktnet.webview_kiosk.managers.DeviceOwnerManager
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.states.LockStateSingleton
import com.nktnet.webview_kiosk.ui.components.apps.AppIcon
import com.nktnet.webview_kiosk.utils.getIsLocked
import com.nktnet.webview_kiosk.utils.normaliseInfoText
import com.nktnet.webview_kiosk.utils.openPackage

@Composable
fun AppLauncherDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (!showDialog) {
        return
    }

    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager

    var activityDialogApp by remember { mutableStateOf<LaunchableAppInfo?>(null) }
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    var apps by remember { mutableStateOf<List<LaunchableAppInfo>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }

    val isLocked by LockStateSingleton.isLocked

    LaunchedEffect(Unit) {
        DeviceOwnerManager
            .getLaunchableAppsFlow(
                context,
                filterLockTaskPermitted = getIsLocked(activityManager)
            )
            .collect { state ->
                apps = apps + state.apps
                progress = state.progress
            }
    }

    BaseAppListDialog(
        onDismiss = onDismiss,
        title = "Apps",
        getDescription = { app ->
            if (app.activities.size > 1) {
                "${app.packageName} (${app.activities.size})"
            } else {
                app.packageName
            }
        },
        apps = apps,
        appFilter = { app, query ->
            (
                app.name.contains(query, ignoreCase = true)
                || app.packageName.contains(query, ignoreCase = true)
            ) && (
                app.packageName != context.packageName
            ) && (
                !isLocked || app.isLockTaskPermitted
            )
        },
        progress = progress,
        onSelectApp = { app ->
            when {
                app.activities.size == 1 -> {
                    openPackage(
                        context, app.packageName,
                        app.activities.first().name,
                    )
                }
                app.activities.size >= 2 -> {
                    activityDialogApp = app
                }
                else -> {
                    ToastManager.show(context, "Error: no activities for app.")
                }
            }
        },
        extraContent = {
            if (isLocked) {
                Column (
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!userSettings.lockTaskFeatureHome) {
                        Text(
                            text = "Error: please enable ${UserSettingsKeys.Device.Owner.LockTaskFeature.HOME}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!dpm.isLockTaskPermitted(context.packageName)) {
                        Text(
                            text = "Error: ${context.packageName} must be lock task permitted to launch apps.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        Text(
                            text = "Error: kiosk launch requires Android API ${Build.VERSION_CODES.P}+ (current: ${Build.VERSION.SDK_INT}).",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        emptyContent = {
            if (isLocked) {
                Text(
                    normaliseInfoText("""
                        No apps available.

                        In kiosk/locked mode, you can only launch apps that have been added to
                        the lock task permitted list (under device owner settings).

                        For user devices that utilise screen pinning, you will not be able to
                        launch other apps.

                        Refer to the documentations for how device owner can be obtained - this
                        requires one of: ADB, Shizuku or Dhizuku.

                        - ${Constants.WEBSITE_URL}
                    """.trimIndent()),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("No apps available.")
            }
        }
    )

    activityDialogApp?.let { app ->
        AlertDialog(
            onDismissRequest = { activityDialogApp = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.icon, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(app.name, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column {
                    Text(
                        "Select an activity to launch:",
                        Modifier.padding(bottom = 12.dp)
                    )
                    app.activities.forEach { activity ->
                        Button(
                            onClick = {
                                openPackage(
                                    context
                                    , app.packageName,
                                    activity.name
                                )
                                activityDialogApp = null
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        ) {
                            Text(
                                activity.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activityDialogApp = null }) {
                    Text("Close")
                }
            },
        )
    }
}
