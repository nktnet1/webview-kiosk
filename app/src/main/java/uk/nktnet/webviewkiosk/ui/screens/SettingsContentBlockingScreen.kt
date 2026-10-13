package uk.nktnet.webviewkiosk.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import uk.nktnet.webviewkiosk.managers.ToastManager
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
    var pendingRemoval by remember { mutableStateOf<ImportedFilterList?>(null) }
    var pendingRename by remember { mutableStateOf<ImportedFilterList?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var downloadingUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun update(
        downloadUrl: String? = null,
        reloadRules: Boolean = true,
        successMessage: String? = null,
        block: () -> Unit,
    ) {
        if (busy) return
        busy = true
        downloadingUrl = downloadUrl
        error = null
        scope.launch {
            try {
                lists = withContext(Dispatchers.IO) {
                    block()
                    // Ensure the engine reload is queued even if navigation cancels the UI.
                    if (reloadRules) blocker.reload()
                    store.list()
                }
                successMessage?.let { ToastManager.show(context, it) }
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

            // Reserve the indicator's height to prevent a layout jump on each toggle.
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                if (busy || blockingStatus == BlockingStatus.LOADING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (!showAddDialog) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (blockingStatus == BlockingStatus.ERROR) {
                Text(
                    nativeError?.takeIf { it.isNotBlank() }?.let { "Blocking unavailable: $it" }
                        ?: "Blocking unavailable",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (lists.isEmpty()) {
                Text("No lists yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            lists.forEach { item ->
                key(item.id) {
                    FilterListRow(
                        item = item,
                        busy = busy,
                        onEnabledChange = { enabled -> update { store.setEnabled(item.id, enabled) } },
                        onRefresh = {
                            update(reloadRules = item.enabled, successMessage = "List updated") {
                                downloader.refresh(item, store)
                            }
                        },
                        onRename = {
                            renameDraft = if (item.sourceUrl == null) item.name else item.name.removeSuffix(".txt")
                            pendingRename = item
                        },
                        onRemove = { pendingRemoval = item },
                    )
                }
            }
        }
    }

    pendingRemoval?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove filter list?") },
            text = { Text(item.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        update { store.remove(item.id) }
                    },
                    enabled = !busy,
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            },
        )
    }

    pendingRename?.let { item ->
        val normalized = renameDraft.trim()
        val displayedName = if (item.sourceUrl == null) item.name else item.name.removeSuffix(".txt")
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename list") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = normalized.isEmpty() || normalized.length > 100,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRename = null
                        update(reloadRules = false, successMessage = "List renamed") {
                            store.rename(item.id, normalized)
                        }
                    },
                    enabled = !busy && normalized.isNotEmpty() && normalized.length <= 100 &&
                        normalized.none { it.isISOControl() } && normalized != displayedName,
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text("Cancel") }
            },
        )
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
private fun FilterListRow(
    item: ImportedFilterList,
    busy: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    // Preserve local filenames; hide the .txt suffix for downloaded preset lists.
    val name = if (item.sourceUrl != null) item.name.removeSuffix(".txt") else item.name

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(
                checked = item.enabled,
                enabled = !busy,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.semantics { contentDescription = "Filter $name" },
            )
            Row(
                modifier = Modifier.weight(1f)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onRename)
                    .semantics { contentDescription = "Rename $name" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    painter = painterResource(R.drawable.baseline_edit_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.sourceUrl != null) {
                IconButton(onClick = onRefresh, enabled = !busy) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_refresh_24),
                        contentDescription = "Update $name",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove, enabled = !busy) {
                Icon(
                    painter = painterResource(R.drawable.baseline_delete_24),
                    contentDescription = "Remove $name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
