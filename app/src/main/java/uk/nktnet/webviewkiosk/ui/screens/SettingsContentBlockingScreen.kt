package uk.nktnet.webviewkiosk.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.blocking.BlockingStatus
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
import uk.nktnet.webviewkiosk.blocking.FilterListStore
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import java.io.IOException

/** User-owned imports: deliberately no suggested/automatically downloaded rule lists. */
@Composable
fun SettingsContentBlockingScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember(context) { FilterListStore(context) }
    val blocker = remember(context) { ContentBlockingService.get(context) }
    val scope = rememberCoroutineScope()
    val blockingStatus by blocker.status.collectAsState()
    val nativeError by blocker.lastError.collectAsState()
    var lists by remember { mutableStateOf(store.list()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun update(block: () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                lists = withContext(Dispatchers.IO) {
                    block()
                    // Always enqueue a reload directly after a successful disk mutation,
                    // even if navigation cancels the UI coroutine before it resumes.
                    blocker.reload()
                    store.list()
                }
            } catch (e: Exception) {
                error = e.message ?: "Unable to update filter lists"
            } finally {
                busy = false
            }
        }
    }

    fun displayName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) null
                    else cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
        }.getOrNull() ?: "Imported filter list"
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            update {
                val name = displayName(uri)
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open the selected file")
                store.importFrom(name, stream)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp, start = 16.dp, end = 16.dp),
    ) {
        SettingLabel(navController, "Content blocking")
        SettingDivider()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Import your own Adblock Plus/uBlock-style or hosts filter lists. " +
                    "No lists are included or downloaded by the app. " +
                    "This first version blocks matching network subresources only.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = {
                    AuthenticationManager.bypassAuthForWindow(60_000)
                    picker.launch(arrayOf("*/*"))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import filter list (.txt)") }
            if (busy) CircularProgressIndicator()
            error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            when (blockingStatus) {
                BlockingStatus.ACTIVE -> Text("Network blocking active")
                BlockingStatus.LOADING -> Text("Compiling filter lists…")
                BlockingStatus.ERROR -> Text(
                    "Blocking unavailable: ${nativeError.orEmpty()}",
                    color = MaterialTheme.colorScheme.error,
                )
                BlockingStatus.DISABLED -> Unit
            }
            if (lists.isEmpty()) {
                Text("No filter lists imported. Content blocking is disabled.")
            }
            lists.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (item.enabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = item.enabled,
                        enabled = !busy,
                        onCheckedChange = { enabled -> update { store.setEnabled(item.id, enabled) } },
                    )
                    TextButton(
                        onClick = { update { store.remove(item.id) } },
                        enabled = !busy,
                    ) { Text("Remove") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
