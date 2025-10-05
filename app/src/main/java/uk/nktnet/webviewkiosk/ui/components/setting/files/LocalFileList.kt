package uk.nktnet.webviewkiosk.ui.components.setting.files

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.utils.getDisplayName
import uk.nktnet.webviewkiosk.utils.getUUID
import uk.nktnet.webviewkiosk.utils.humanReadableSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LocalFileList(
    filesList: List<File>,
    filesDir: File,
    modifier: Modifier = Modifier,
    refreshFiles: () -> Unit
) {
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

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
                        text = "Size: ${humanReadableSize(file.length())}",
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
                            Icons.Filled.MoreVert,
                            contentDescription = "Menu",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded && activeFile == file,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showRenameDialog = true
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showDeleteDialog = true
                                menuExpanded = false
                            }
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
                        singleLine = true
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
                            toast("Rename failed")
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
                Text("Are you sure you want to delete ${activeFile?.getDisplayName()}?")
            },
            confirmButton = {
                TextButton(onClick = {
                    activeFile?.let { file ->
                        if (file.delete()) {
                            refreshFiles()
                        } else {
                            toast("Failed to delete ${file.getDisplayName()}")
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
