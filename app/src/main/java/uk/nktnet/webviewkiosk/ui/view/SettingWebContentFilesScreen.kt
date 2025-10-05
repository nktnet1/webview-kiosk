package uk.nktnet.webviewkiosk.ui.view

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.files.LocalFileList
import uk.nktnet.webviewkiosk.utils.displayName
import uk.nktnet.webviewkiosk.utils.listLocalFiles
import uk.nktnet.webviewkiosk.utils.uploadFile

@Composable
fun SettingsWebContentFilesScreen(navController: NavController) {
    val context = LocalContext.current
    var filesList by remember { mutableStateOf(listLocalFiles(context.filesDir)) }
    val showToast = rememberToast()

    val fileUploadLauncher: ManagedActivityResultLauncher<Array<String>, Uri?> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri: Uri? ->
                if (uri == null) {
                    showToast("File selection cancelled")
                    return@rememberLauncherForActivityResult
                }
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    val file = uploadFile(context, uri, context.filesDir)
                    filesList = listLocalFiles(context.filesDir)
                    showToast("File uploaded: ${file.displayName()}")
                } catch (e: Exception) {
                    showToast("File upload failed: ${e.message}")
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
            Button(onClick = { fileUploadLauncher.launch(arrayOf("text/html")) }) {
                Text("Upload")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.outline_upload_file_24),
                    contentDescription = "Upload"
                )
            }
        }

        Text(
            text = "Total files: ${filesList.size}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.End)
                .padding(
                    top = 4.dp, end = 4.dp,
                )
        )

        if (filesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
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
                onDeleteClick = { file ->
                    if (file.delete()) {
                        filesList = listLocalFiles(context.filesDir)
                        showToast("Deleted ${file.displayName()}")
                    } else {
                        showToast("Failed to delete ${file.displayName()}")
                    }
                }
            )
        }
    }
}


@Composable
fun rememberToast(): (String) -> Unit {
    val context = LocalContext.current
    val toastRef = remember { mutableStateOf<Toast?>(null) }
    return { message ->
        toastRef.value?.cancel()
        toastRef.value = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
    }
}