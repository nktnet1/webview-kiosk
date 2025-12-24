package com.nktnet.webview_kiosk.ui.screens

import android.net.Uri
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.files.LocalFileList
import com.nktnet.webview_kiosk.utils.getWebContentFilesDir
import com.nktnet.webview_kiosk.utils.listLocalFiles
import com.nktnet.webview_kiosk.utils.supportedMimeTypesArray
import com.nktnet.webview_kiosk.utils.uploadFile
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
                    } else {
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
