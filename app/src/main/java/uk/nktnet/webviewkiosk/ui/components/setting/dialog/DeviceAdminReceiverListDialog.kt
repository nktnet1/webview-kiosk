package com.nktnet.webview_kiosk.ui.components.setting.dialog

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.nktnet.webview_kiosk.config.data.AdminAppInfo
import com.nktnet.webview_kiosk.managers.DeviceOwnerManager
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.ui.components.apps.AppIcon

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun DeviceAdminReceiverListDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (!showDialog) {
        return
    }

    val context = LocalContext.current
    var selectedApp by remember { mutableStateOf<AdminAppInfo?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    var apps by remember { mutableStateOf<List<AdminAppInfo>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        DeviceOwnerManager.getDeviceAdminReceiversFlow(context).collect { state ->
            apps = apps + state.apps
            progress = state.progress
        }
    }

    BaseAppListDialog(
        onDismiss = onDismiss,
        title = "Transfer Ownership",
        apps = apps,
        progress = progress,
        getDescription = { it.admin.className },
        getKey = { it.admin.className },
        appFilter = { app, query ->
            (
                app.name.contains(query, ignoreCase = true)
                || app.admin.className.contains(query, ignoreCase = true)
            ) && (
                app.packageName != context.packageName
            )
        },
        onSelectApp = {
            selectedApp = it
            showConfirmDialog = true
        }
    )

    ConfirmTransferDialog(
        show = showConfirmDialog,
        selectedAdminReceiver = selectedApp,
        onDismiss = {
            showConfirmDialog = false
            selectedApp = null
        },
        onConfirm = {
            showConfirmDialog = false
            selectedApp = null
            onDismiss()
            DeviceOwnerManager.init(context)
        },
    )
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun ConfirmTransferDialog(
    show: Boolean,
    selectedAdminReceiver: AdminAppInfo?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show || selectedAdminReceiver == null) return

    val context = LocalContext.current
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(selectedAdminReceiver.icon, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text(selectedAdminReceiver.name, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Are you sure you want to transfer ownership to ${selectedAdminReceiver.name}?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AdminLabelValueRow(
                        "App",
                        selectedAdminReceiver.name
                    )
                    AdminLabelValueRow(
                        "Package",
                        selectedAdminReceiver.packageName
                    )
                    AdminLabelValueRow(
                        "Receiver",
                        selectedAdminReceiver.admin.className
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    dpm.transferOwnership(
                        DeviceOwnerManager.DAR,
                        selectedAdminReceiver.admin,
                        null
                    )
                    onConfirm()
                } catch (e: Exception) {
                    ToastManager.show(context, "Error: ${e.message}")
                }
            }) {
                Text("Transfer", color = MaterialTheme.colorScheme.error)
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
private fun AdminLabelValueRow(label: String, value: String) {
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
