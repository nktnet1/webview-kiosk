package uk.nktnet.webviewkiosk.ui.components.webview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun LinkOptionsDialog(
    link: String?,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    if (link != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = null,
            text = {
                Text(
                    text = link,
                )
            },
            confirmButton = {
                Column (
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = {
                            onOpenLink(link)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text("Open Link")
                    }
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Copied Link", link))
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Link")
                    }
                }
            }
        )
    }
}
