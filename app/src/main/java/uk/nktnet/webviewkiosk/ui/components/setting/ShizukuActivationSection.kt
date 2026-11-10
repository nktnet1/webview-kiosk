package uk.nktnet.webviewkiosk.ui.components.setting

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.activation.ActivationResult
import uk.nktnet.webviewkiosk.activation.ShizukuActivation
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeAlertDialog as AlertDialog

@RequiresApi(Build.VERSION_CODES.S)
@Composable
internal fun ShizukuActivationSection(
    hasNativeOwner: Boolean,
    onActivated: () -> Unit,
    activate: (suspend (Boolean) -> ActivationResult)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var profileOwner by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ActivationResult?>(null) }

    if (!hasNativeOwner || running) {
        Button(
            onClick = { showDialog = true },
            enabled = !running,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(stringResource(R.string.ui_activate_with_shizuku))
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!running) {
                    showDialog = false
                }
            },
            title = { Text(stringResource(R.string.ui_activate_with_shizuku)) },
            text = {
                Column(
                    Modifier.heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(stringResource(R.string.ui_shizuku_activation_info))
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.selectableGroup()) {
                        for (profile in listOf(false, true)) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .selectable(
                                            selected = profileOwner == profile,
                                            enabled = !running,
                                            role = Role.RadioButton,
                                            onClick = { profileOwner = profile },
                                        )
                                        .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = profileOwner == profile,
                                    onClick = null,
                                    enabled = !running,
                                )
                                Text(
                                    stringResource(
                                        if (profile) {
                                            R.string.about_app_profile_owner
                                        } else {
                                            R.string.about_app_device_owner
                                        }
                                    ),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    if (running) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator()
                        Text(stringResource(R.string.ui_shizuku_activating))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !running && !hasNativeOwner,
                    onClick = {
                        if (!running && !hasNativeOwner) {
                            running = true
                            scope.launch {
                                try {
                                    result =
                                        activate?.invoke(profileOwner)
                                            ?: ShizukuActivation.activate(
                                                context.applicationContext,
                                                profileOwner,
                                            )
                                    showDialog = false
                                    onActivated()
                                } finally {
                                    running = false
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.ui_shizuku_activate))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !running,
                    onClick = { showDialog = false },
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
        )
    }

    result?.let { outcome ->
        var detailsExpanded by remember(outcome) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { result = null },
            title = {
                Text(
                    stringResource(
                        if (outcome.success) {
                            R.string.ui_shizuku_activation_success
                        } else {
                            R.string.ui_shizuku_activation_failed
                        }
                    )
                )
            },
            text = {
                Column(
                    Modifier.heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer { Text(outcome.output) }
                    if (outcome.diagnostics.isNotBlank()) {
                        TextButton(
                            onClick = { detailsExpanded = !detailsExpanded }
                        ) {
                            Text(
                                stringResource(
                                    if (detailsExpanded) {
                                        R.string.ui_shizuku_hide_details
                                    } else {
                                        R.string.ui_shizuku_show_details
                                    }
                                )
                            )
                        }
                        if (detailsExpanded) {
                            SelectionContainer {
                                Text(
                                    outcome.diagnostics,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { result = null }) {
                    Text(stringResource(R.string.ui_close))
                }
            },
        )
    }
}
