package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.data.LaunchableAppInfo
import uk.nktnet.webviewkiosk.managers.AppFlowManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.ui.components.apps.AppIcon
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeAlertDialog as AlertDialog
import uk.nktnet.webviewkiosk.utils.getIsLocked
import uk.nktnet.webviewkiosk.utils.normaliseInfoText
import uk.nktnet.webviewkiosk.utils.openPackage

@Composable
fun AppLauncherDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
) {
    if (!showDialog) {
        return
    }

    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val activityManager =
        context.getSystemService(ACTIVITY_SERVICE) as ActivityManager

    var activityDialogApp by remember { mutableStateOf<LaunchableAppInfo?>(null) }
    val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE)
            as DevicePolicyManager

    var apps by remember { mutableStateOf<List<LaunchableAppInfo>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }

    val isLocked by LockStateSingleton.isLocked

    LaunchedEffect(Unit) {
        AppFlowManager.getLaunchableAppsFlow(
                context,
                filterLockTaskPermitted = getIsLocked(activityManager),
            )
            .collect { state ->
                apps = apps + state.apps
                progress = state.progress
            }
    }

    BaseAppListDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.ui_apps),
        getDescription = { app ->
            if (app.activities.size > 1) {
                "${app.packageName} (${app.activities.size})"
            } else {
                app.packageName
            }
        },
        apps = apps,
        appFilter = { app, query ->
            (app.name.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)) &&
                (app.packageName != context.packageName) &&
                (!isLocked || app.isLockTaskPermitted)
        },
        progress = progress,
        onSelectApp = { app ->
            when {
                app.activities.size == 1 -> {
                    openPackage(
                        context,
                        app.packageName,
                        app.activities.first().name,
                    )
                }
                app.activities.size >= 2 -> {
                    activityDialogApp = app
                }
                else -> {
                    ToastManager.show(
                        context,
                        context.getString(R.string.ui_error_no_activities_for_app),
                    )
                }
            }
        },
        extraContent = {
            if (isLocked) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!userSettings.lockTaskFeatureHome) {
                        Text(
                            text =
                                stringResource(
                                    R.string.ui_error_please_enable_value,
                                    UserSettingsKeys.Device.Owner.LockTaskFeature
                                        .HOME,
                                ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!dpm.isLockTaskPermitted(context.packageName)) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .ui_error_value_must_be_lock_task_permitted_to_launch_apps,
                                    context.packageName,
                                ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .ui_error_kiosk_launch_requires_android_api_value_current,
                                    Build.VERSION_CODES.P,
                                    Build.VERSION.SDK_INT,
                                ),
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
                    normaliseInfoText(
                        stringResource(
                            R.string.ui_app_launcher_no_apps_in_kiosk,
                            Constants.DOCUMENTATION_URL,
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(stringResource(R.string.ui_no_apps_available))
            }
        },
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
                        stringResource(R.string.ui_select_an_activity_to_launch),
                        Modifier.padding(bottom = 12.dp),
                    )
                    app.activities.forEach { activity ->
                        Button(
                            onClick = {
                                openPackage(
                                    context,
                                    app.packageName,
                                    activity.name,
                                )
                                activityDialogApp = null
                            },
                            modifier =
                                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        ) {
                            Text(
                                activity.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activityDialogApp = null }) {
                    Text(stringResource(R.string.ui_close))
                }
            },
        )
    }
}
