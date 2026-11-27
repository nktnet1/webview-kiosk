package uk.nktnet.webviewkiosk.ui.components.setting.files

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeAlertDialog as AlertDialog
import uk.nktnet.webviewkiosk.utils.getDisplayName
import uk.nktnet.webviewkiosk.utils.getLegacyLocalFileUrl
import uk.nktnet.webviewkiosk.utils.getLocalFileLink
import uk.nktnet.webviewkiosk.utils.getUUID
import uk.nktnet.webviewkiosk.utils.humanReadableSize
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen

@Composable
fun LocalFileList(
    navController: NavController,
    filesList: List<File>,
    filesDir: File,
    showLegacyLocalFileLinks: Boolean,
    modifier: Modifier = Modifier,
    refreshFiles: () -> Unit,
) {
    val context = LocalContext.current
    val systemSettings = SystemSettings(context)

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var menuFile by remember { mutableStateOf<File?>(null) }
    var fileToEdit by remember { mutableStateOf<File?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    LazyColumn(modifier = modifier) {
        items(filesList, key = { it.getUUID() }) { file ->
            val uuidPart = file.getUUID()
            val displayName = file.getDisplayName()
            val fileLink =
                if (showLegacyLocalFileLinks) {
                    file.getLegacyLocalFileUrl()
                } else {
                    file.getLocalFileLink()
                }
            val openLinkLabel =
                if (showLegacyLocalFileLinks) {
                    stringResource(R.string.ui_open_file)
                } else {
                    stringResource(R.string.ui_open_link_2)
                }
            val copyLinkLabel =
                if (showLegacyLocalFileLinks) {
                    stringResource(R.string.ui_copy_file)
                } else {
                    stringResource(R.string.ui_copy_link_2)
                }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.ui_id_value, uuidPart),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.ui_size_value,
                                humanReadableSize(context, file.length()),
                            ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.ui_time_value,
                                SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm",
                                        Locale.getDefault(),
                                    )
                                    .format(Date(file.lastModified())),
                            ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box {
                    IconButton(onClick = { menuFile = file }) {
                        Icon(
                            painter =
                                painterResource(R.drawable.outline_more_vert_24),
                            contentDescription = stringResource(R.string.ui_menu),
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = menuFile == file,
                        onDismissRequest = { menuFile = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text(openLinkLabel) },
                            onClick = {
                                menuFile = null
                                systemSettings.intentUrl = fileLink
                                navigateToWebViewScreen(navController)
                            },
                            leadingIcon = {
                                Icon(
                                    painter =
                                        painterResource(
                                            R.drawable.baseline_file_open_24
                                        ),
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(copyLinkLabel) },
                            onClick = {
                                menuFile = null
                                scope.launch {
                                    val clipData =
                                        ClipData.newPlainText(
                                            context.getString(R.string.ui_file_link),
                                            fileLink,
                                        )
                                    clipboard.setClipEntry(clipData.toClipEntry())
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    painter =
                                        painterResource(
                                            R.drawable.baseline_content_copy_24
                                        ),
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_edit_file)) },
                            onClick = {
                                fileToEdit = file
                                menuFile = null
                            },
                            leadingIcon = {
                                Icon(
                                    painter =
                                        painterResource(R.drawable.baseline_edit_24),
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_delete)) },
                            onClick = {
                                fileToDelete = file
                                menuFile = null
                            },
                            leadingIcon = {
                                Icon(
                                    painter =
                                        painterResource(
                                            R.drawable.baseline_clear_24
                                        ),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier,
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color,
            )
        }
    }

    fileToEdit?.let { file ->
        LocalFileEditorDialog(
            filesDir = filesDir,
            file = file,
            onDismiss = { fileToEdit = null },
            onSaved = {
                refreshFiles()
                fileToEdit = null
            },
        )
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(stringResource(R.string.ui_delete_file)) },
            text = {
                Text(
                    stringResource(
                        R.string.ui_confirm_delete_local_file,
                        file.getDisplayName(),
                    )
                )
            },
            confirmButton = {
                TextButton(
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                    onClick = {
                        if (file.delete()) {
                            refreshFiles()
                        } else {
                            ToastManager.show(
                                context,
                                context.getString(
                                    R.string.ui_failed_to_delete_value,
                                    file.getDisplayName(),
                                ),
                            )
                        }
                        fileToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.ui_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
        )
    }
}
