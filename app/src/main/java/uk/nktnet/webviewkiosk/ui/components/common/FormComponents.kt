package uk.nktnet.webviewkiosk.ui.components.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants

@Composable
fun LabelWithInfo(
    label: String,
    infoTitle: String,
    infoText: String,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = { showInfo = true }, modifier = Modifier.size(20.dp).padding(start=4.dp)) {
            Icon(
                painter = painterResource(R.drawable.outline_info_24),
                contentDescription = "$label info",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("OK")
                }
            },
            title = { Text(infoTitle) },
            text = { Text(infoText) }
        )
    }
}

@Composable
fun UrlInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("e.g. ${Constants.WEBSITE_URL}", fontStyle = FontStyle.Italic) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError
    )
    if (isError) {
        Text(
            "Must start with http:// or https:// and be a valid URL with a proper domain",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )
    }
}

@Composable
fun PatternInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontStyle = FontStyle.Italic) },
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        minLines = 3,
    )
    if (isError) {
        Text(
            "Invalid regex pattern",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 0.dp, top = 4.dp)
        )
    }
}
