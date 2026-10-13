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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.blocking.BlockingStatus
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
import uk.nktnet.webviewkiosk.blocking.FilterListStore
import uk.nktnet.webviewkiosk.blocking.ImportedFilterList
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterList
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterListDownloader
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterLists
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import java.io.IOException

@Composable
fun SettingsContentBlockingScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember(context) { FilterListStore(context) }
    val blocker = remember(context) { ContentBlockingService.get(context) }
    val downloader = remember { SuggestedFilterListDownloader() }
    val scope = rememberCoroutineScope()
    val blockingStatus by blocker.status.collectAsState()
    val nativeError by blocker.lastError.collectAsState()
    var lists by remember { mutableStateOf(store.list()) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var downloadingUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun update(downloadUrl: String? = null, block: () -> Unit) {
        if (busy) return
        busy = true
        downloadingUrl = downloadUrl
        error = null
        scope.launch {
            try {
                lists = withContext(Dispatchers.IO) {
                    block()
                    // Ensure the engine reload is queued even if navigation cancels the UI.
                    blocker.reload()
                    store.list()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Unable to update filter lists"
            } finally {
                busy = false
                downloadingUrl = null
            }
        }
    }

    fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
    }.getOrNull() ?: "Imported filter list"

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            update {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open the selected file")
                store.importFrom(displayName(uri), stream)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp, start = 16.dp, end = 16.dp),
    ) {
        SettingLabel(navController, stringResource(R.string.settings_content_blocking_title))
        SettingDivider()
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filter lists", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = {
                        error = null
                        showAddDialog = true
                    },
                    enabled = !busy,
                ) { Text("Add list") }
            }

            if (busy && !showAddDialog) {
                Text("Updating…", style = MaterialTheme.typography.bodySmall)
            }
            if (!showAddDialog) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            when (blockingStatus) {
                BlockingStatus.ACTIVE -> Text("Blocking active", style = MaterialTheme.typography.bodySmall)
                BlockingStatus.LOADING -> Text("Compiling lists…", style = MaterialTheme.typography.bodySmall)
                BlockingStatus.ERROR -> Text(
                    "Blocking unavailable: ${nativeError.orEmpty()}",
                    color = MaterialTheme.colorScheme.error,
                )
                BlockingStatus.DISABLED -> Unit
            }

            if (lists.isEmpty()) {
                Text("No lists added", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            lists.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Switch(
                        checked = item.enabled,
                        enabled = !busy,
                        onCheckedChange = { enabled -> update { store.setEnabled(item.id, enabled) } },
                        modifier = Modifier.semantics { contentDescription = "Enable ${item.name}" },
                    )
                    TextButton(
                        onClick = { update { store.remove(item.id) } },
                        enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Remove ${item.name}" },
                    ) { Text("Remove") }
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showAddDialog) {
        AddFilterListDialog(
            lists = lists,
            busy = busy,
            downloadingUrl = downloadingUrl,
            error = error,
            onDismiss = { showAddDialog = false },
            onPickFile = {
                showAddDialog = false
                AuthenticationManager.bypassAuthForWindow(60_000)
                picker.launch(arrayOf("*/*"))
            },
            onDownload = { suggestion ->
                update(downloadUrl = suggestion.url) { downloader.download(suggestion, store) }
            },
        )
    }
}

@Composable
private fun AddFilterListDialog(
    lists: List<ImportedFilterList>,
    busy: Boolean,
    downloadingUrl: String?,
    error: String?,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onDownload: (SuggestedFilterList) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add filter list") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onPickFile,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choose file") }

                Text("Popular lists", style = MaterialTheme.typography.titleSmall)
                SuggestedFilterLists.all.forEach { suggestion ->
                    val installed = lists.any { it.sourceUrl == suggestion.url }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                suggestion.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                suggestion.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = { onDownload(suggestion) },
                            enabled = !busy && !installed,
                            modifier = Modifier.semantics {
                                contentDescription = if (installed) "${suggestion.name} added"
                                    else "Download ${suggestion.name}"
                            },
                        ) {
                            if (downloadingUrl == suggestion.url) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (installed) "Added" else "Download")
                            }
                        }
                    }
                }
                Text(
                    "Updates are manual",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
