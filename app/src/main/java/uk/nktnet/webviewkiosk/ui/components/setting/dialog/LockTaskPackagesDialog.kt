package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.data.AppInfo
import uk.nktnet.webviewkiosk.config.data.AppType
import uk.nktnet.webviewkiosk.managers.AppFlowManager
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.apps.AppIcon
import uk.nktnet.webviewkiosk.utils.normaliseInfoText

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun LockTaskPackagesDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (!showDialog) {
        return
    }

    val context = LocalContext.current
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showAddLockTaskPackagesDialog by remember { mutableStateOf(false) }

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        AppFlowManager.getLockTaskAppsFlow(context).collect { state ->
            apps = apps + state.apps
            progress = state.progress
        }
    }

    val existingPackages by remember(apps) {
        derivedStateOf { apps.map { it.packageName }.toSet() }
    }

    BaseAppListDialog(
        onDismiss = onDismiss,
        title = "Lock Task Packages",
        apps = apps,
        progress = progress,
        onSelectApp = {
            selectedApp = it
            showConfirmDialog = true
        },
        appFilter = { app, query ->
            app.name.contains(query, ignoreCase = true)
            || app.packageName.contains(query, ignoreCase = true)
        },
        extraContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showAddLockTaskPackagesDialog = true }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_add_24),
                        contentDescription = "Add",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Add")
                }
            }
        }
    )

    RemoveLockTaskAppDialog(
        show = showConfirmDialog,
        apps = apps,
        removeApp = selectedApp,
        onDismiss = {
            showConfirmDialog = false
            selectedApp = null
        },
        onConfirm = { removedApp ->
            apps = apps.filter { it.packageName != removedApp.packageName }
        },
    )

    AddLockTaskPackagesDialog(
        showDialog = showAddLockTaskPackagesDialog,
        existingPackages = existingPackages,
        onDismiss = { showAddLockTaskPackagesDialog = false },
        onConfirm = { app -> apps += app }
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RemoveLockTaskAppDialog(
    show: Boolean,
    apps: List<AppInfo>,
    removeApp: AppInfo?,
    onDismiss: () -> Unit,
    onConfirm: (AppInfo) -> Unit,
) {
    if (!show || removeApp == null) {
        return
    }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(removeApp.icon, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text(removeApp.name, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    normaliseInfoText(
                        """
                        Are you sure you want to remove ${removeApp.name} from the allow
                        list of lock task packages?

                        This means lock task mode will no longer be available for this
                        application, and kiosk mode will fall back to screen pinning.
                        """.trimIndent()
                    ),
                    Modifier.padding(vertical = 8.dp),
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppLabelRow(
                        "App",
                        removeApp.name
                    )
                    AppLabelRow(
                        "Package",
                        removeApp.packageName
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!DeviceOwnerManager.hasOwnerPermission(context)) {
                        ToastManager.show(context, "Error: owner permission is not granted.")
                        return@TextButton
                    }

                    try {
                        DeviceOwnerManager.DPM.setLockTaskPackages(
                            DeviceOwnerManager.DAR,
                            apps
                                .filter { app -> app.packageName != removeApp.packageName }
                                .map { it.packageName }.toTypedArray()
                        )
                        ToastManager.show(
                            context,
                            "${removeApp.name} has been removed from lock task packages."
                        )
                        onConfirm(removeApp)
                    } catch (e: Exception) {
                        ToastManager.show(context, "Error: ${e.message}")
                    } finally {
                        onDismiss()
                    }
                }
            ) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddLockTaskPackagesDialog(
    showDialog: Boolean,
    existingPackages: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (app: AppInfo) -> Unit,
) {
    if (!showDialog) {
        return
    }

    val context = LocalContext.current

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }
    var appType by remember { mutableStateOf(AppType.USER_APPS) }
    var expandedDropdown by remember { mutableStateOf(false) }
    var dropdownWidth by remember { mutableIntStateOf(0) }

    LaunchedEffect(appType) {
        apps = emptyList()
        progress = 0f
        AppFlowManager.getInstalledAppsFlow(
            context,
            appType,
        ).collect { state ->
            apps = apps + state.apps
            progress = state.progress
        }
    }

    BaseAppListDialog(
        onDismiss = onDismiss,
        title = "Add Lock Task Package",
        apps = apps,
        appFilter = { app, query ->
            (
                app.name.contains(query, ignoreCase = true)
                || app.packageName.contains(query, ignoreCase = true)
            ) && (
                app.packageName !in existingPackages
            )
        },
        progress = progress,
        onSelectApp = { newApp ->
            if (!DeviceOwnerManager.hasOwnerPermission(context)) {
                ToastManager.show(context, "Error: owner permission is not granted.")
                return@BaseAppListDialog
            }

            try {
                DeviceOwnerManager.DPM.setLockTaskPackages(
                    DeviceOwnerManager.DAR,
                    (existingPackages + newApp.packageName).toTypedArray()
                )
                ToastManager.show(
                    context,
                    "${newApp.name} has been added to lock task packages."
                )
                onConfirm(newApp)
            } catch (e: Exception) {
                ToastManager.show(context, "Error: ${e.message}")
            } finally {
                onDismiss()
            }
        },
        extraFilters = {
            Row(
               modifier = Modifier
                   .padding(top = 6.dp, bottom = 4.dp)
                   .onSizeChanged { dropdownWidth = it.width },

                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(43.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            MaterialTheme.shapes.small
                        )
                        .clickable { expandedDropdown = true },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = appType.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painter = painterResource(R.drawable.outline_keyboard_arrow_down_24),
                            contentDescription = "Select"
                        )
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier.width(with(LocalDensity.current) {
                            dropdownWidth.toDp()
                        })
                    ) {
                        AppType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(type.label, style = MaterialTheme.typography.bodyMedium)
                                },
                                onClick = {
                                    appType = type
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun AppLabelRow(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(3f)
        )
    }
}
