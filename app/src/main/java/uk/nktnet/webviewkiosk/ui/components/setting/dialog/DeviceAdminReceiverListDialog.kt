package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import uk.nktnet.webviewkiosk.config.data.AdminAppInfo
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.apps.AppIcon
import uk.nktnet.webviewkiosk.ui.components.apps.AppList
import uk.nktnet.webviewkiosk.ui.components.apps.AppSearchBar

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

    var apps by remember { mutableStateOf<List<AdminAppInfo>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var ascending by remember { mutableStateOf(true) }
    var selectedApp by remember { mutableStateOf<AdminAppInfo?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val filteredApps by remember(searchQuery.text, apps, ascending) {
        derivedStateOf {
            apps
                .filter {
                    it.name.contains(searchQuery.text, ignoreCase = true)
                    || it.admin.className.contains(searchQuery.text, ignoreCase = true)
                }
                .sortedBy { it.name }
                .let { if (ascending) it else it.reversed() }
        }
    }

    LaunchedEffect(Unit) {
        val currentApps = mutableListOf<AdminAppInfo>()
        DeviceOwnerManager.getDeviceAdminReceiversFlow(context)
            .collect { state ->
                currentApps.addAll(state.apps)
                apps = currentApps.toList()
                progress = state.progress
                if (progress < 1f && currentApps.isNotEmpty()) {
                    listState.scrollToItem(0)
                }
            }
    }

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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text("Transfer Ownership", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                AppSearchBar(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = TextFieldValue(it) },
                    onSortToggle = { ascending = !ascending },
                    appCount = apps.size,
                    ascending = ascending,
                )

                if (progress < 1f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(4.dp)
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                }

                if (filteredApps.isEmpty() && progress == 1f) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text("No apps available.")
                    }
                } else {
                    AppList(
                        apps = filteredApps,
                        onSelectApp = {
                            selectedApp = it
                            showConfirmDialog = true
                        },
                        getKey = { it.admin.className },
                        getDescription = { it.admin.className },
                        listState = listState,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun ConfirmTransferDialog(
    show: Boolean,
    selectedAdminReceiver: AdminAppInfo?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show || selectedAdminReceiver == null) {
        return
    }

    val context = LocalContext.current
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    selectedAdminReceiver.icon,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    selectedAdminReceiver.name,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    """
                        Are you sure you want to transfer ownership to ${selectedAdminReceiver.name}?
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    """
                        This action cannot be undone.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AdminLabelValueRow("App", selectedAdminReceiver.name)
                    AdminLabelValueRow("Package", selectedAdminReceiver.admin.packageName)
                    AdminLabelValueRow("Receiver", selectedAdminReceiver.admin.className)
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
                Text(
                    "Transfer",
                    color = MaterialTheme.colorScheme.error
                )
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
