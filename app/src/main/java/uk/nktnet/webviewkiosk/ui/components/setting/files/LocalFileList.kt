package com.nktnet.webview_kiosk.ui.components.setting.files

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.utils.getDisplayName
import com.nktnet.webview_kiosk.utils.getLocalUrl
import com.nktnet.webview_kiosk.utils.getUUID
import com.nktnet.webview_kiosk.utils.humanReadableSize
import com.nktnet.webview_kiosk.utils.navigateToWebViewScreen
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LocalFileList(
    navController: NavController,
    filesList: List<File>,
    filesDir: File,
    modifier: Modifier = Modifier,
    refreshFiles: () -> Unit
) {
    val context = LocalContext.current
    val systemSettings = SystemSettings(context)

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var activeFile by remember { mutableStateOf<File?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier) {
        items(filesList, key = { it.getUUID() }) { file ->
            val uuidPart = file.getUUID()
            val displayName = file.getDisplayName()

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
                        text = displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: $uuidPart",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Size: ${humanReadableSize(context, file.length())}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = {
                        activeFile = file
                        newName = displayName
                        menuExpanded = true
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.outline_more_vert_24),
                            contentDescription = "Menu",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded && activeFile == file,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open File") },
                            onClick = {
                                menuExpanded = false
                                systemSettings.intentUrl = file.getLocalUrl()
                                navigateToWebViewScreen(navController)
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_file_open_24),
                                    contentDescription = null
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy URL") },
                            onClick = {
                                scope.launch {
                                    val clipData = ClipData.newPlainText("File URL", file.getLocalUrl())
                                    clipboard.setClipEntry(clipData.toClipEntry())
                                    menuExpanded = false
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_content_copy_24),
                                    contentDescription = null
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showRenameDialog = true
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_drive_file_rename_outline_24),
                                    contentDescription = null
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showDeleteDialog = true
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_clear_24),
                                    contentDescription = null
                                )
                            },
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier,
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )
        }
    }

    if (showRenameDialog && activeFile != null) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                activeFile = null
            },
            title = { Text("Rename File") },
            text = {
                Column {
                    TextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                enabled = newName.isNotEmpty(),
                                onClick = {
                                    newName = ""
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_clear_24),
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "NOTE: this may break existing links/bookmarks.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    activeFile?.let { file ->
                        val newFile = File(filesDir, "${file.getUUID()}|$newName")
                        if (file.renameTo(newFile)) {
                            refreshFiles()
                        } else {
                            ToastManager.show(context, "Rename failed")
                        }
                        showRenameDialog = false
                        activeFile = null
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    activeFile = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog && activeFile != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                activeFile = null
            },
            title = { Text("Delete File") },
            text = {
                Text("""
                    Are you sure you want to delete this file?

                      ${activeFile?.getDisplayName()}

                    This will only remove the app's copy and not the original file on your device.
                """.trimIndent())
            },
            confirmButton = {
                TextButton(onClick = {
                    activeFile?.let { file ->
                        if (file.delete()) {
                            refreshFiles()
                        } else {
                            ToastManager.show(context, "Failed to delete ${file.getDisplayName()}")
                        }
                        showDeleteDialog = false
                        activeFile = null
                    }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    activeFile = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
