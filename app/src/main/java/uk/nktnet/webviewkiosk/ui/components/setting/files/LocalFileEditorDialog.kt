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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeDialog as Dialog
import uk.nktnet.webviewkiosk.utils.getDisplayName
import uk.nktnet.webviewkiosk.utils.isValidLocalFileName
import uk.nktnet.webviewkiosk.utils.readEditableTextFile
import uk.nktnet.webviewkiosk.utils.saveLocalFile

@Composable
fun LocalFileEditorDialog(
    filesDir: File,
    file: File? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialName = file?.getDisplayName().orEmpty()
    var name by remember(file) { mutableStateOf(initialName) }
    var initialText by remember(file) { mutableStateOf("".takeIf { file == null }) }
    var text by remember(file) { mutableStateOf("") }
    var isLoading by remember(file) { mutableStateOf(file != null) }
    var isSaving by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        if (file != null) {
            val contents = withContext(Dispatchers.IO) { readEditableTextFile(file) }
            text = contents.orEmpty()
            initialText = contents
            isLoading = false
        }
    }

    val nameChanged = name != initialName
    val validName = (file != null && !nameChanged) || isValidLocalFileName(name)
    val nameError = name.isNotEmpty() && !validName
    val nameMessage =
        when {
            nameError -> R.string.ui_invalid_local_file_name
            file != null && nameChanged && validName ->
                R.string.ui_local_file_rename_warning
            else -> null
        }
    val showContentsEditor = initialText != null
    val contentsChanged = showContentsEditor && text != initialText
    val dialogSize =
        if (showContentsEditor) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth()
        }

    Dialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = !showContentsEditor),
    ) {
        Surface(
            modifier = dialogSize.padding(16.dp),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text =
                        stringResource(
                            if (file == null) {
                                R.string.ui_new_file
                            } else {
                                R.string.ui_edit_file
                            }
                        ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.ui_local_file_name)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText =
                        if (nameMessage != null) {
                            { Text(stringResource(nameMessage)) }
                        } else {
                            null
                        },
                    trailingIcon = {
                        IconButton(
                            enabled = !isSaving && name.isNotEmpty(),
                            onClick = { name = "" },
                        ) {
                            Icon(
                                painter =
                                    painterResource(R.drawable.baseline_clear_24),
                                contentDescription =
                                    stringResource(
                                        R.string.ui_clear_local_file_name
                                    ),
                            )
                        }
                    },
                )
                if (showContentsEditor) {
                    val clipboard = LocalClipboard.current
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        enabled = !isSaving,
                        label = { Text(stringResource(R.string.ui_file_contents)) },
                        textStyle = MaterialTheme.typography.bodySmall,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            enabled = !isSaving && contentsChanged,
                            onClick = { text = initialText.orEmpty() },
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                        ) {
                            Icon(
                                painter =
                                    painterResource(R.drawable.baseline_refresh_24),
                                contentDescription =
                                    stringResource(R.string.ui_reset),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            enabled = !isSaving && text.isNotEmpty(),
                            onClick = { text = "" },
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                        ) {
                            Icon(
                                painter =
                                    painterResource(R.drawable.baseline_clear_24),
                                contentDescription =
                                    stringResource(R.string.ui_clear),
                            )
                        }
                        IconButton(
                            enabled = !isSaving,
                            onClick = {
                                scope.launch {
                                    val clipEntry = clipboard.getClipEntry()
                                    if (!isSaving) {
                                        text =
                                            clipEntry
                                                ?.clipData
                                                ?.getItemAt(0)
                                                ?.text
                                                ?.toString()
                                                .orEmpty()
                                    }
                                }
                            },
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable.outline_content_paste_24
                                    ),
                                contentDescription =
                                    stringResource(R.string.ui_paste),
                            )
                        }
                    }
                } else if (isLoading) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.ui_loading_file_contents),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        Text(stringResource(R.string.ui_cancel))
                    }
                    TextButton(
                        onClick = {
                            if (isSaving) {
                                return@TextButton
                            }
                            val savedName = name
                            val savedContents = text.takeIf {
                                file == null ||
                                    (initialText != null && it != initialText)
                            }
                            isSaving = true
                            scope.launch {
                                val saved =
                                    withContext(Dispatchers.IO) {
                                        saveLocalFile(
                                            filesDir,
                                            file,
                                            savedName,
                                            savedContents,
                                        )
                                    }
                                isSaving = false
                                if (saved) {
                                    ToastManager.show(
                                        context,
                                        context.getString(R.string.ui_file_saved),
                                    )
                                    onSaved()
                                } else {
                                    ToastManager.show(
                                        context,
                                        context.getString(
                                            R.string.ui_failed_to_save_file
                                        ),
                                    )
                                }
                            }
                        },
                        enabled =
                            !isLoading &&
                                !isSaving &&
                                validName &&
                                (file == null || nameChanged || contentsChanged),
                    ) {
                        Text(
                            stringResource(
                                when {
                                    isSaving -> R.string.ui_saving
                                    file == null -> R.string.ui_create
                                    else -> R.string.ui_save
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}
