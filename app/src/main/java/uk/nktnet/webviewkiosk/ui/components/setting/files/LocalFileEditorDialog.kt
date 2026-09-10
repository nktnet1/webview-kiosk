package uk.nktnet.webviewkiosk.ui.components.setting.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.utils.getDisplayName
import uk.nktnet.webviewkiosk.utils.writeEditableTextFile
import java.io.File

@Composable
fun LocalFileEditorDialog(
    file: File,
    initialText: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var text by remember(file, initialText) { mutableStateOf(initialText) }
    var isSaving by remember(file) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Edit File",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = file.getDisplayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    enabled = !isSaving,
                    textStyle = MaterialTheme.typography.bodySmall,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = !isSaving && text != initialText,
                        onClick = { text = initialText },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_refresh_24),
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        enabled = !isSaving && text.isNotEmpty(),
                        onClick = { text = "" },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_clear_24),
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(
                        enabled = !isSaving,
                        onClick = {
                            scope.launch {
                                val clipEntry = clipboard.getClipEntry()
                                text = clipEntry
                                    ?.clipData
                                    ?.getItemAt(0)
                                    ?.text
                                    ?.toString()
                                    ?: ""
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_content_paste_24),
                            contentDescription = "Paste",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                    ) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            if (isSaving) {
                                return@TextButton
                            }
                            isSaving = true
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    writeEditableTextFile(file, text)
                                }
                                isSaving = false
                                if (saved) {
                                    ToastManager.show(context, "File saved")
                                    onSaved()
                                } else {
                                    ToastManager.show(context, "Failed to save file")
                                }
                            }
                        },
                        enabled = !isSaving && text != initialText,
                    ) {
                        Text(if (isSaving) "Saving..." else "Save")
                    }
                }
            }
        }
    }
}
