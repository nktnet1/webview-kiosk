package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.ui.components.webview.HistoryDialog
import uk.nktnet.webviewkiosk.utils.IconUtils
import uk.nktnet.webviewkiosk.utils.validateUrl

@Composable
fun CreateShortcutDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (!showDialog) {
        return
    }

    val context = LocalContext.current
    val systemSettings = remember { SystemSettings(context) }

    var shortLabel by remember { mutableStateOf("") }
    var longLabel by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    var shortLabelError by remember { mutableStateOf<String?>(null) }
    var longLabelError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var isOpenHistoryDialog by remember { mutableStateOf(false) }

    var previewIcon by remember { mutableStateOf(IconUtils.buildLetterIcon(shortLabel)) }

    val canCreate = (
        shortLabelError == null
        && longLabelError == null
        && urlError == null
        && shortLabel.isNotBlank()
        && longLabel.isNotBlank()
        && url.isNotBlank()
    )

    fun setShortLabel(value: String) {
        shortLabel = value
        shortLabelError = validateShortLabel(value)
        previewIcon = IconUtils.buildLetterIcon(value)
    }

    fun setUrl(value: String) {
        val trimmed = value.trim()
        url = trimmed
        urlError = if (!validateUrl(trimmed)) {
            "Invalid URL"
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Shortcut") },
        text = {
            Column (
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                SimpleOutlinedTextField(
                    value = shortLabel,
                    onValueChange = {
                        setShortLabel(it)
                    },
                    label = "Short Label",
                    error = shortLabelError
                )

                Spacer(modifier = Modifier.height(4.dp))

                SimpleOutlinedTextField(
                    value = longLabel,
                    onValueChange = {
                        longLabel = it
                        longLabelError = validateLongLabel(it)
                    },
                    label = "Long Label",
                    error = longLabelError
                )

                Spacer(modifier = Modifier.height(4.dp))

                SimpleOutlinedTextField(
                    value = url,
                    onValueChange = {
                        setUrl(it)
                    },
                    label = "URL",
                    error = urlError
                )

                Button(
                    onClick = {
                        setUrl(systemSettings.currentUrl)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Use the current URL:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = systemSettings.currentUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Button(
                    onClick = { isOpenHistoryDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(top = 6.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text(
                        text = "Select from History",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val drawable = previewIcon.loadDrawable(context)
                val bitmap = drawable?.toBitmap()
                bitmap?.let {
                    Spacer(modifier = Modifier.height(20.dp))
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Shortcut preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canCreate,
                onClick = {
                    val safeShortLabel = shortLabel.trim()
                    val safeLongLabel = longLabel.trim()
                    val safeUrl = url.trim()

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = safeUrl.toUri()
                        setPackage(context.packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                    val shortcut = ShortcutInfoCompat.Builder(context, safeUrl)
                        .setShortLabel(safeShortLabel)
                        .setLongLabel(safeLongLabel)
                        .setIcon(IconUtils.buildLetterIcon(shortLabel))
                        .setIntent(intent)
                        .build()

                    ShortcutManagerCompat.requestPinShortcut(
                        context,
                        shortcut,
                        null
                    )
                    onDismiss()
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    HistoryDialog(
        isOpenHistoryDialog,
        { isOpenHistoryDialog = false },
        { item, _ ->
            setUrl(item.url)
        },
        disableCurrent = false,
        highlightCurrent = false,
    )
}

@Composable
private fun SimpleOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String?
) {
    Column { }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(
                onClick = { onValueChange("") },
                enabled = value.isNotEmpty()
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_clear_24),
                    contentDescription = "Clear"
                )
            }
        }
    )
    if (error != null) {
        Text(
            text = error,
            modifier = Modifier.padding(
                top = 6.dp,
                bottom = 4.dp
            ),
            color = MaterialTheme.colorScheme.error
        )
    }
}

private fun validateLabel(
    value: String,
    fieldName: String,
    maxLength: Int
): String? {
    val trimmed = value.trim()

    if (trimmed.isBlank()) {
        return "$fieldName cannot be empty"
    }
    if (trimmed.length > maxLength) {
        return "Max $maxLength characters"
    }

    if (trimmed.any { it.isISOControl() }) {
        return "Invalid characters in $fieldName label"
    }

    return null
}

private fun validateShortLabel(value: String): String? {
    return validateLabel(
        value = value,
        fieldName = "Short label",
        maxLength = 10
    )
}

private fun validateLongLabel(value: String): String? {
    return validateLabel(
        value = value,
        fieldName = "Long label",
        maxLength = 25
    )
}
