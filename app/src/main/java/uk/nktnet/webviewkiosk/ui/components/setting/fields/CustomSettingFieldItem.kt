package uk.nktnet.webviewkiosk.ui.components.setting.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.GenericSettingFieldDialog

@Composable
fun CustomSettingFieldItem(
    label: String,
    infoText: String,
    value: String,
    onSave: () -> Unit,
    onDismissCallback: () -> Unit = {},
    bodyContent: @Composable () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    GenericSettingFieldItem(
        label = label,
        value = value.ifBlank { "(blank)" },
        onClick = { showDialog = true }
    ) { displayValue ->
        Text(
            text = displayValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDialog) {
        GenericSettingFieldDialog(
            title = label,
            infoText = infoText,
            onDismiss = {
                showDialog = false
                onDismissCallback()
            },
            onSave = {
                onSave()
                showDialog = false
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                bodyContent()
            }
        }
    }
}
