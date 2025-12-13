package com.nktnet.webview_kiosk.ui.components.setting.permissions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.utils.getPermissionDisplay

@Composable
fun SitePermissionsList(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemSettings = remember { SystemSettings(context) }
    var sitePermissions by remember { mutableStateOf(systemSettings.sitePermissionsMap) }

    var activeSite by remember { mutableStateOf<String?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    if (sitePermissions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "No site permissions have been granted.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = modifier.fillMaxWidth()
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(sitePermissions.toList(), key = { it.first }) { (origin, resources) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        Text(
                            text =origin,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        resources.forEach { res ->
                            Text(
                                "• ${getPermissionDisplay(res)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = {
                            activeSite = origin
                            menuExpanded = true
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.outline_more_vert_24),
                                contentDescription = "Menu",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded && activeSite == origin,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = {
                                showEditDialog = true
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                showDeleteDialog = true
                                menuExpanded = false
                            })
                        }
                    }
                }

                HorizontalDivider(color = DividerDefaults.color, thickness = DividerDefaults.Thickness)
            }
        }
    }

    if (showEditDialog && activeSite != null) {
        var tempResources by remember { mutableStateOf(sitePermissions[activeSite] ?: emptySet()) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = activeSite ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column {
                    if (tempResources.isEmpty()) {
                        Text(
                            text = "All permissions will be removed.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        return@Column
                    }
                    tempResources.forEach { res ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                getPermissionDisplay(res),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { tempResources = tempResources - res }) {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_clear_24),
                                    contentDescription = "Remove permission",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    activeSite?.let {
                        systemSettings.setSitePermissions(it, tempResources)
                        sitePermissions = systemSettings.sitePermissionsMap
                    }
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog && activeSite != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "$activeSite",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = { Text("Are you sure you want to remove ALL permissions for this site?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeSite?.let {
                            systemSettings.setSitePermissions(it, emptySet())
                            sitePermissions = systemSettings.sitePermissionsMap
                            ToastManager.show(context, "Deleted $it")
                        }
                        showDeleteDialog = false
                    },
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}
