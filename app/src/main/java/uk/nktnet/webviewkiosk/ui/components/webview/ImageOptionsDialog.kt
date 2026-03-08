package uk.nktnet.webviewkiosk.ui.components.webview

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.utils.safeStartActivity
import uk.nktnet.webviewkiosk.utils.webview.handlers.handleDownloadPrompt

fun getMimeType(context: android.content.Context, uri: Uri): String? {
    return if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
        context.contentResolver.getType(uri)
    } else {
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }
}

@Composable
fun ImageOptionsDialog(
    imageUrl: String?,
    onDismiss: () -> Unit,
    onOpenImage: (String) -> Unit
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val isLocked by LockStateSingleton.isLocked

    if (imageUrl != null) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = imageUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                val clipData = ClipData.newPlainText("Image URL", imageUrl)
                                clipboard.setClipEntry(clipData.toClipEntry())
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Link")
                    }

                    Button(
                        onClick = {
                            onOpenImage(imageUrl)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Image")
                    }

                    if (!isLocked) {
                        val uri = imageUrl.toUri()
                        if (uri.scheme != "file") {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    safeStartActivity(context, intent)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open in Browser")
                            }
                        }

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, imageUrl)
                                }
                                val chooser = Intent.createChooser(intent, "Share Link")
                                safeStartActivity(context, chooser)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Share Link")
                        }
                    }

                    if (userSettings.allowFileDownload) {
                        Button(
                            onClick = {
                                val uri = imageUrl.toUri()
                                val mimeType = getMimeType(context, uri) ?: "image/*"

                                handleDownloadPrompt(
                                    context = context,
                                    url = imageUrl,
                                    userAgent = null,
                                    contentDisposition = null,
                                    mimeType = mimeType
                                )
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Download Image")
                        }
                    }
                }
            }
        }
    }
}
