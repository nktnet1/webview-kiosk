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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import uk.nktnet.webviewkiosk.blocking.FilterRefreshResult
import uk.nktnet.webviewkiosk.blocking.FilterListType
import uk.nktnet.webviewkiosk.blocking.ImportedFilterList
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterList
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterListDownloader
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterLists
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import java.io.IOException

private fun ImportedFilterList.displayName(): String =
    if (sourceUrl == null) name else name.removeSuffix(".txt")

private fun validListName(name: String): Boolean =
    name.isNotBlank() && name.trim().length <= 100 && name.none { it.isISOControl() }

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
    var pendingEdit by remember { mutableStateOf<ImportedFilterList?>(null) }
    var editName by remember { mutableStateOf("") }
    var editUrl by remember { mutableStateOf("") }
    var editText by remember { mutableStateOf<String?>(null) }
    var originalText by remember { mutableStateOf<String?>(null) }
    var editFileLoaded by remember { mutableStateOf(false) }
    var replacementUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun update(
        reloadRules: Boolean = true,
        successMessage: String? = null,
        onSuccess: (() -> Unit)? = null,
        block: () -> Unit,
    ) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                lists = withContext(Dispatchers.IO) {
                    block()
                    // Queue the reload on IO even if this screen is then navigated away from.
                    if (reloadRules) blocker.reload()
                    store.list()
                }
                onSuccess?.invoke()
                successMessage?.let { ToastManager.show(context, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Unable to update filter lists"
            } finally {
                busy = false
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

    val addFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            update(successMessage = "List added") {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open the selected file")
                store.importFrom(displayName(uri), stream)
            }
        }
    }
    val replaceFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && pendingEdit?.type == FilterListType.FILE) replacementUri = uri
    }

    fun openEditor(item: ImportedFilterList) {
        pendingEdit = item
        editName = item.displayName()
        editUrl = item.sourceUrl.orEmpty()
        replacementUri = null
        editText = null
        originalText = null
        editFileLoaded = item.type != FilterListType.FILE
        error = null
        if (item.type == FilterListType.FILE) {
            scope.launch {
                try {
                    val contents = withContext(Dispatchers.IO) { store.editableFileText(item.id) }
                    if (pendingEdit?.id == item.id) {
                        originalText = contents
                        editText = contents
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (pendingEdit?.id == item.id) error = e.message ?: "Cannot open filter list"
                } finally {
                    if (pendingEdit?.id == item.id) editFileLoaded = true
                }
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
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        var summary = FilterRefreshResult(0, 0, emptyList())
                        update(onSuccess = {
                            ToastManager.show(context, "Updated ${summary.updated} of ${summary.total} lists")
                            if (summary.failures.isNotEmpty()) {
                                error = summary.failures.joinToString("\n") { "${it.name}: ${it.message}" }
                            }
                        }) {
                            summary = downloader.refreshAll(store)
                        }
                    },
                    enabled = !busy && lists.any { it.sourceUrl != null },
                ) {
                    Icon(painterResource(R.drawable.baseline_refresh_24), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Update all")
                }
                Button(
                    onClick = {
                        error = null
                        showAddDialog = true
                    },
                    enabled = !busy,
                ) { Text("Add list") }
            }

            // Reserve the indicator's height so enable/disable and downloads never shift rows.
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                if (busy || blockingStatus == BlockingStatus.LOADING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (!showAddDialog && pendingEdit == null) {
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
                        onEdit = { openEditor(item) },
                        onClone = {
                            update(reloadRules = false, successMessage = "List cloned (disabled)") {
                                store.cloneList(item.id)
                            }
                        },
                        onRemove = { pendingRemoval = item },
                    )
                }
            }
        }
    }

    pendingRemoval?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRemoval = null },
            title = { Text("Remove filter list?") },
            text = { Text(item.displayName()) },
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
                TextButton(onClick = { pendingRemoval = null }, enabled = !busy) { Text("Cancel") }
            },
        )
    }

    pendingEdit?.let { item ->
        val changedFile = replacementUri != null || (originalText != editText && editText != null)
        val changedUrl = item.sourceUrl != null && editUrl.trim() != item.sourceUrl
        val canSave = !busy && editFileLoaded && validListName(editName) &&
            item.type != FilterListType.PRESET &&
            (item.type == FilterListType.FILE || editUrl.trim().startsWith("https://", ignoreCase = true)) &&
            (editName.trim() != item.displayName() || changedUrl || changedFile)
        EditFilterListDialog(
            item = item,
            name = editName,
            onNameChange = { editName = it },
            url = editUrl,
            onUrlChange = { editUrl = it },
            fileText = editText,
            fileLoaded = editFileLoaded,
            onFileTextChange = { editText = it },
            replacementName = replacementUri?.let(::displayName),
            onPickReplacement = {
                AuthenticationManager.bypassAuthForWindow(60_000)
                replaceFilePicker.launch(arrayOf("*/*"))
            },
            onClearReplacement = { replacementUri = null },
            error = error,
            busy = busy,
            canSave = canSave,
            onDismiss = { if (!busy) pendingEdit = null },
            onSave = {
                val name = editName.trim()
                val url = editUrl.trim()
                val sourceUri = replacementUri
                val typedContent = if (originalText != editText && editText != null) editText else null
                val reloadRules = item.enabled && when (item.type) {
                    FilterListType.FILE -> changedFile
                    FilterListType.URL -> changedUrl
                    FilterListType.PRESET -> false
                }
                update(
                    reloadRules = reloadRules,
                    successMessage = "List saved",
                    onSuccess = { pendingEdit = null },
                ) {
                    when (item.type) {
                        FilterListType.FILE -> {
                            val input = when {
                                sourceUri != null -> context.contentResolver.openInputStream(sourceUri)
                                    ?: throw IOException("Cannot open the selected file")
                                typedContent != null -> typedContent.byteInputStream(Charsets.UTF_8)
                                else -> null
                            }
                            store.updateFile(item.id, name, input)
                        }
                        FilterListType.URL -> downloader.editUrl(item, name, url, store)
                        FilterListType.PRESET -> throw IllegalArgumentException("Clone a preset to edit it")
                    }
                }
            },
        )
    }

    if (showAddDialog) {
        AddFilterListDialog(
            lists = lists,
            busy = busy,
            error = error,
            onDismiss = { if (!busy) showAddDialog = false },
            onPickFile = {
                showAddDialog = false
                AuthenticationManager.bypassAuthForWindow(60_000)
                addFilePicker.launch(arrayOf("*/*"))
            },
            onDownloadUrl = { url, name ->
                update(successMessage = "List added", onSuccess = { showAddDialog = false }) {
                    downloader.downloadUrl(url, name, store)
                }
            },
            onDownloadPreset = { suggestion ->
                update(successMessage = "List added", onSuccess = { showAddDialog = false }) {
                    downloader.download(suggestion, store)
                }
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
    onEdit: () -> Unit,
    onClone: () -> Unit,
    onRemove: () -> Unit,
) {
    val name = item.displayName()
    var menuExpanded by remember { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Switch(
                checked = item.enabled,
                enabled = !busy,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.semantics { contentDescription = "Filter $name" },
            )
            Column(
                modifier = Modifier.weight(1f)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onEdit)
                    .semantics { contentDescription = "Edit $name" },
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (item.type) {
                        FilterListType.FILE -> "File"
                        FilterListType.URL -> "URL"
                        FilterListType.PRESET -> "Preset list"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = !busy) {
                    Icon(painterResource(R.drawable.outline_more_vert_24), contentDescription = "Options for $name")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(painterResource(R.drawable.baseline_edit_24), contentDescription = null) },
                        onClick = { menuExpanded = false; onEdit() },
                        enabled = !busy,
                    )
                    if (item.sourceUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Update") },
                            leadingIcon = { Icon(painterResource(R.drawable.baseline_refresh_24), contentDescription = null) },
                            onClick = { menuExpanded = false; onRefresh() },
                            enabled = !busy,
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Clone") },
                        leadingIcon = { Icon(painterResource(R.drawable.baseline_content_copy_24), contentDescription = null) },
                        onClick = { menuExpanded = false; onClone() },
                        enabled = !busy,
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(painterResource(R.drawable.baseline_delete_24), contentDescription = null) },
                        onClick = { menuExpanded = false; onRemove() },
                        enabled = !busy,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListTypeDropdown(value: FilterListType, onChange: (FilterListType) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (value) {
        FilterListType.FILE -> "File"
        FilterListType.URL -> "URL"
        FilterListType.PRESET -> "Preset list"
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "List type" },
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.outline_keyboard_arrow_down_24), contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FilterListType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(when (type) {
                            FilterListType.FILE -> "File"
                            FilterListType.URL -> "URL"
                            FilterListType.PRESET -> "Preset list"
                        })
                    },
                    onClick = { expanded = false; onChange(type) },
                )
            }
        }
    }
}

@Composable
private fun FilterListField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

@Composable
private fun AddFilterListDialog(
    lists: List<ImportedFilterList>,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onDownloadUrl: (String, String) -> Unit,
    onDownloadPreset: (SuggestedFilterList) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(FilterListType.FILE) }
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var suggestion by remember { mutableStateOf(SuggestedFilterLists.all.first()) }
    var presetsExpanded by remember { mutableStateOf(false) }
    val installed = lists.any { it.sourceUrl == suggestion.url }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add filter list") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterListField("List type") {
                    ListTypeDropdown(value = type, onChange = { type = it }, enabled = !busy)
                }
                when (type) {
                    FilterListType.FILE -> FilterListField("File") {
                        OutlinedButton(onClick = onPickFile, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(painterResource(R.drawable.outline_upload_file_24), contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Choose file")
                        }
                        Text(
                            "Choose a plain-text filter list from your device.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilterListType.URL -> {
                        FilterListField("HTTPS list URL") {
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "HTTPS list URL" },
                                singleLine = true,
                                enabled = !busy,
                            )
                        }
                        FilterListField("Name") {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Name" },
                                placeholder = { Text("e.g. My filters") },
                                singleLine = true,
                                enabled = !busy,
                            )
                        }
                    }
                    FilterListType.PRESET -> {
                        FilterListField("Preset list") {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { presetsExpanded = true }, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Preset list" },
                                ) {
                                    Text(suggestion.name, modifier = Modifier.weight(1f), maxLines = 1)
                                    Icon(painterResource(R.drawable.outline_keyboard_arrow_down_24), contentDescription = null)
                                }
                                DropdownMenu(expanded = presetsExpanded, onDismissRequest = { presetsExpanded = false }) {
                                    SuggestedFilterLists.all.forEach { preset ->
                                        DropdownMenuItem(
                                            text = { Text(preset.name) },
                                            onClick = { suggestion = preset; presetsExpanded = false },
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            suggestion.description + if (installed) " · Already added" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilterValidationNotice()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (type != FilterListType.FILE) {
                Button(
                    onClick = {
                        when (type) {
                            FilterListType.FILE -> Unit
                            FilterListType.URL -> onDownloadUrl(url.trim(), name.trim())
                            FilterListType.PRESET -> onDownloadPreset(suggestion)
                        }
                    },
                    enabled = !busy && when (type) {
                        FilterListType.FILE -> true
                        FilterListType.URL -> url.trim().startsWith("https://", ignoreCase = true) && validListName(name)
                        FilterListType.PRESET -> !installed
                    },
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            painterResource(R.drawable.outline_cloud_download_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Download")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun FilterValidationNotice() {
    Text(
                    "At least one supported network or CSS-hiding rule is required. Scriptlets, custom styles, and procedural filters are ignored. Reload the page after changing cosmetic rules.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EditFilterListDialog(
    item: ImportedFilterList,
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    fileText: String?,
    fileLoaded: Boolean,
    onFileTextChange: (String) -> Unit,
    replacementName: String?,
    onPickReplacement: () -> Unit,
    onClearReplacement: () -> Unit,
    error: String?,
    busy: Boolean,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val editable = item.type != FilterListType.PRESET
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit filter list") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!editable) {
                    Text(
                        "Preset lists are read-only.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    readOnly = !editable,
                    isError = !validListName(name),
                )
                if (item.type != FilterListType.FILE) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        label = { Text("HTTPS list URL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        readOnly = !editable,
                        maxLines = 3,
                    )
                } else if (!fileLoaded) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    FilterValidationNotice()
                    if (fileText != null) {
                        OutlinedTextField(
                            value = fileText,
                            onValueChange = onFileTextChange,
                            label = { Text("Filter rules") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                            minLines = 5,
                            maxLines = 10,
                            enabled = !busy && replacementName == null,
                        )
                    } else {
                        Text(
                            "This list is too large to edit inline. Choose a replacement text file instead.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onPickReplacement, enabled = !busy) {
                        Text(if (replacementName == null) "Replace from file" else "Change replacement file")
                    }
                    if (replacementName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(replacementName, modifier = Modifier.weight(1f), maxLines = 2)
                            TextButton(onClick = onClearReplacement, enabled = !busy) { Text("Clear") }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
