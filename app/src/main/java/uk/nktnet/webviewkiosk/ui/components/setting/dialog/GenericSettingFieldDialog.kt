package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeAlertDialog as AlertDialog

@Composable
fun GenericSettingFieldDialog(
    title: String,
    infoText: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    settingKey: String,
    restricted: Boolean,
    saveText: String? = null,
    disabledReason: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedSaveText = saveText ?: stringResource(R.string.ui_save)
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                SelectionContainer {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            settingKey,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.6f
                                ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        // Setting resources use line breaks only for intentional
                        // layout.
                        Text(infoText)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.ui_close))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.offset(x = 2.dp).size(24.dp).padding(3.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_info_24),
                        contentDescription = stringResource(R.string.ui_info),
                    )
                }
            }
        },
        text = {
            Column {
                if (restricted) {
                    Text(
                        text =
                            stringResource(
                                R.string.ui_this_setting_is_managed_by_your_it_admin
                            ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                if (disabledReason != null) {
                    Text(
                        text = disabledReason,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                content()
            }
        },
        confirmButton = {
            if (!restricted && disabledReason == null) {
                TextButton(onClick = onSave) {
                    Text(resolvedSaveText)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    if (restricted || disabledReason != null) {
                        stringResource(R.string.ui_close)
                    } else {
                        stringResource(R.string.ui_cancel)
                    }
                )
            }
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}
