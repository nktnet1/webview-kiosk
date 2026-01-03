package uk.nktnet.webviewkiosk.ui.screens

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.files.LocalFileList
import uk.nktnet.webviewkiosk.utils.getWebContentFilesDir
import uk.nktnet.webviewkiosk.utils.listLocalFiles
import uk.nktnet.webviewkiosk.utils.supportedMimeTypesArray
import uk.nktnet.webviewkiosk.utils.uploadFile
import java.util.concurrent.CancellationException

@Composable
fun SettingsWebContentFilesScreen(navController: NavController) {
    val context = LocalContext.current
    val filesDir = getWebContentFilesDir(context)

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
                    withContext(Dispatchers.IO) {
                        uploadFile(context, uri, filesDir) { p ->
                            progress = p
                        }
                    }
                    filesList = listLocalFiles(filesDir)
                    ToastManager.show(context, "File uploaded")
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        // Ignore cancellation caused by leaving the UI
                        e.printStackTrace()
                    } else {
                        e.printStackTrace()
                        ToastManager.show(context, "Upload failed: ${e.message}")
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_files_title)
        )
        SettingDivider()

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
                            supportedMimeTypesArray
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
                navController = navController,
                filesList = filesList,
                filesDir = filesDir,
                modifier = Modifier.padding(top = 8.dp),
                refreshFiles = ::refreshFiles
            )
        }
    }
}
