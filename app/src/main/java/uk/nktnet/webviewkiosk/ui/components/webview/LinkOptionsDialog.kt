package uk.nktnet.webviewkiosk.ui.components.webview

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LinkOptionsDialog(
    link: String?,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

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
                            scope.launch {
                                val clipData = ClipData.newPlainText("Link", link)
                                clipboard.setClipEntry(clipData.toClipEntry())
                                onDismiss()
                            }
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
