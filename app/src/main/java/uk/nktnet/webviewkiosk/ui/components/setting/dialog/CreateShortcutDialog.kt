package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.R
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

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    val isNameValid = nameError == null && name.isNotBlank()
    val isUrlValid = urlError == null && url.isNotBlank()
    val canCreate = isNameValid && isUrlValid

    fun validateName(value: String): String? {
        val trimmed = value.trim()

        if (trimmed.isBlank()) {
            return "Name cannot be empty"
        }
        if (trimmed.length > 30) {
            return "Max 30 characters"
        }

        if (trimmed.any { it.isISOControl() }) {
            return "Invalid characters in name"
        }

        return null
    }

    fun validateUrlLocal(value: String): String? {
        val trimmed = value.trim()
        return if (!validateUrl(trimmed)) {
            "Invalid URL"
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Shortcut") },
        text = {
            Column {
                SimpleOutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = validateName(it)
                    },
                    label = "Name",
                    error = nameError
                )

                Spacer(modifier = Modifier.height(8.dp))

                SimpleOutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = validateUrlLocal(it)
                    },
                    label = "URL",
                    error = urlError
                )

                if (urlError != null) {
                    Text(
                        text = urlError.toString(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canCreate,
                onClick = {
                    val safeName = name.trim()
                    val safeUrl = url.trim()

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = safeUrl.toUri()
                        setPackage(context.packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                    val shortcut = ShortcutInfoCompat.Builder(context, safeUrl)
                        .setShortLabel(safeName)
                        .setLongLabel(safeName)
                        .setIcon(
                            IconCompat.createWithResource(
                                context,
                                android.R.drawable.ic_menu_view
                            )
                        )
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
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.error
        )
    }
}
