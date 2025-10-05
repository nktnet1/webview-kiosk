package uk.nktnet.webviewkiosk.utils

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun rememberFileUploadLauncher(
    customLoadHtmlFile: (uriString: String, html: String) -> Unit,
    onUrlBarTextChange: (TextFieldValue) -> Unit
): ManagedActivityResultLauncher<Array<String>, Uri?> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) {
                Toast.makeText(context, "HTML file selection cancelled", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val html = context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
                val uriString = uri.toString()
                if (html != null) {
                    customLoadHtmlFile(uriString, html)
                    onUrlBarTextChange(TextFieldValue(uriString))
                } else {
                    Toast.makeText(context, "Failed to read selected HTML file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "HTML file upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
