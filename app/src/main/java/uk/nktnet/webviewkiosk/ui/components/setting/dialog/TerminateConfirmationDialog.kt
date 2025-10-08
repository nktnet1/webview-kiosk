package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import android.os.Process
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlin.system.exitProcess

@Composable
fun TerminateConfirmationDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Terminate App") },
            text = {
                Text("""
                    Are you sure you want to terminate this application?
                    
                    This will forcefully kill the process by its PID (hard-shutdown).
                """.trimIndent())
            },
            confirmButton = {
                TextButton(onClick = {
                    Process.killProcess(Process.myPid())
                    exitProcess(0)
                }) {
                    Text(
                        "Force Stop",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        "Cancel",
                    )
                }
            }
        )
    }
}
