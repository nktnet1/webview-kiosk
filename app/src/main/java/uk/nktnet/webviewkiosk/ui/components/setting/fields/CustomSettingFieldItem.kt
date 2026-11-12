package uk.nktnet.webviewkiosk.ui.components.setting.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.GenericSettingFieldDialog
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun CustomSettingFieldItem(
    settingKey: String,
    label: String = stringResource(settingDefinition(settingKey).titleRes),
    infoText: String = stringResource(settingDefinition(settingKey).descriptionRes),
    value: String,
    restricted: Boolean,
    onSave: () -> Unit,
    onDismissCallback: () -> Unit = {},
    bodyContent: @Composable () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    GenericSettingFieldItem(
        settingKey = settingKey,
        label = label,
        value = value.ifBlank { "(blank)" },
        restricted = restricted,
        onClick = { showDialog = true },
    ) { displayValue ->
        Text(
            text = displayValue,
            style =
                if (value.isEmpty()) {
                    MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDialog) {
        GenericSettingFieldDialog(
            title = label,
            infoText = infoText,
            restricted = restricted,
            onDismiss = {
                showDialog = false
                onDismissCallback()
            },
            settingKey = settingKey,
            onSave = {
                onSave()
                showDialog = false
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                bodyContent()
            }
        }
    }
}
