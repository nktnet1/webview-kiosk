package uk.nktnet.webviewkiosk.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.utils.saveContentIntentToFile
import java.io.File

@Composable
fun UploadFileProgressScreen(
    context: AppCompatActivity,
    uri: Uri,
    targetDir: File,
    onProgress: (Float) -> Unit,
    onComplete: (File) -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(uri) {
        try {
            val file = saveContentIntentToFile(context, uri, targetDir) { p ->
                progress = p
                onProgress(p)
            }
            onComplete(file)
        } catch (e: Exception) {
            Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Uploading file...", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(0.7f),
            color = ProgressIndicatorDefaults.linearColor,
            trackColor = ProgressIndicatorDefaults.linearTrackColor,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
        Spacer(Modifier.height(8.dp))
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
    }
}
