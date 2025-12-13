package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.data.LaunchableAppInfo
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.utils.openPackage

@Composable
fun AppLauncherDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activityDialogApp by remember { mutableStateOf<LaunchableAppInfo?>(null) }

    BaseAppListDialog(
        showDialog = showDialog,
        onDismiss = onDismiss,
        title = "Apps",
        fetchAppsFlow = { DeviceOwnerManager.getLaunchableAppsFlow(context) },
        getDescription = { app ->
            if (app.activities.size > 1) {
                "${app.packageName} (${app.activities.size})"
            } else {
                app.packageName
            }
        },
        onSelectApp = { app ->
            when {
                app.activities.size == 1 -> {
                    openPackage(
                        context, app.packageName,
                        app.activities.first().name
                    )
                }
                app.activities.size >= 2 -> {
                    activityDialogApp = app
                }
                else -> {
                    ToastManager.show(context, "Error: no activities for app.")
                }
            }
        }
    )

    activityDialogApp?.let { app ->
        AlertDialog(
            onDismissRequest = { activityDialogApp = null },
            title = { Text(app.name) },
            text = {
                Column {
                    Text(
                        "Select an activity to launch:",
                        Modifier.padding(bottom = 12.dp)
                    )
                    app.activities.forEach { activity ->
                        Button(
                            onClick = {
                                openPackage(context, app.packageName, activity.name)
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
            }
        )
    }
}
