package uk.nktnet.webviewkiosk.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.files.LocalFileList
import uk.nktnet.webviewkiosk.utils.listLocalFiles
import uk.nktnet.webviewkiosk.utils.uploadFile
import java.io.File
import java.util.concurrent.CancellationException

@Composable
fun SettingsWebContentFilesScreen(navController: NavController) {
    val context = LocalContext.current
    val filesDir = File(context.filesDir, Constants.WEB_CONTENT_FILES_DIR).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    var filesList by remember { mutableStateOf(listLocalFiles(filesDir)) }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val coroutineScope = rememberCoroutineScope()

    fun refreshFiles() {
        filesList = listLocalFiles(filesDir)
    }

    val startUpload: (Uri) -> Unit = remember {
        { uri ->
            coroutineScope.launch {
                uploading = true
                progress = 0f
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    withContext(Dispatchers.IO) {
                        uploadFile(context, uri, filesDir) { p ->
                            progress = p
                        }
                    }
                    filesList = listLocalFiles(filesDir)
                    Toast.makeText(context, "File uploaded", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        // Ignore cancellation caused by leaving the UI
                    } else {
                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    uploading = false
                    progress = 0f
                }
            }
        }
    }

    val fileUploadLauncher: ManagedActivityResultLauncher<Array<String>, Uri?> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri: Uri? ->
                if (uri != null) {
                    startUpload(uri)
                }
            }
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
    ) {
        SettingLabel(navController = navController, label = "Files")

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        fileUploadLauncher.launch(
                            arrayOf(
                                "text/*",
                                "image/*",
                                "audio/*",
                                "video/*",
                                "application/json",
                                "application/javascript",
                                "application/xml",
                                "application/sql",
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uploading
                ) {
                    Text("Upload")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.outline_upload_file_24),
                        contentDescription = "Upload"
                    )
                }

                if (uploading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                }
            }
        }

        Text(
            text = "Total files: ${filesList.size}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp, end = 4.dp)
        )

        if (filesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "No files uploaded yet.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LocalFileList(
                filesList = filesList,
                filesDir = filesDir,
                modifier = Modifier.padding(top = 8.dp),
                refreshFiles = ::refreshFiles
            )
        }
    }
}
