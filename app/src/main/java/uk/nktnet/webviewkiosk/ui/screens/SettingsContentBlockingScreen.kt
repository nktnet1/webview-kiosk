package uk.nktnet.webviewkiosk.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.io.IOException
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.blocking.BlockingStatus
import uk.nktnet.webviewkiosk.blocking.ContentBlockingEditSession
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
import uk.nktnet.webviewkiosk.blocking.FilterListInfo
import uk.nktnet.webviewkiosk.blocking.FilterListStore
import uk.nktnet.webviewkiosk.blocking.FilterListType
import uk.nktnet.webviewkiosk.blocking.FilterRefreshResult
import uk.nktnet.webviewkiosk.blocking.ImportedFilterList
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterList
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterListDownloader
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterLists
import uk.nktnet.webviewkiosk.blocking.toFilterListUrlOrNull
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.common.MinimumLoadingState
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen

private fun ImportedFilterList.displayName(): String =
    if (sourceUrl == null) {
        name
    } else {
        name.removeSuffix(".txt")
    }

private fun validListName(name: String): Boolean =
    name.isNotBlank() && name.trim().length <= 100 && name.none { it.isISOControl() }

private fun List<ImportedFilterList>.hasUrlList(
    url: String,
    exceptId: String? = null,
): Boolean {
    val normalized = url.toFilterListUrlOrNull()?.toString() ?: return false
    return any {
        it.id != exceptId &&
            it.type == FilterListType.URL &&
            it.sourceUrl == normalized
    }
}

@Composable
fun SettingsContentBlockingScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember(context) { FilterListStore(context) }
    val blocker = remember(context) { ContentBlockingService.get(context) }
    val edits = remember(blocker) { ContentBlockingEditSession(blocker) }
    val downloader = remember { SuggestedFilterListDownloader() }
    val scope = rememberCoroutineScope()
    val blockingEnabled by blocker.enabled.collectAsState()
    val blockingStatus by blocker.status.collectAsState()
    val loadStage by blocker.loadStage.collectAsState()
    val blockingError by blocker.lastError.collectAsState()
    var lists by remember { mutableStateOf(store.list()) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var workingMessage by remember { mutableStateOf("") }
    val loading = remember { MinimumLoadingState() }
    val displayedLoadingMessage by loading.message.collectAsState()
    val currentLoadingMessage =
        when {
            blockingStatus == BlockingStatus.LOADING ->
                loadStage?.description ?: "Loading filter rules…"

            working -> workingMessage

            else -> null
        }
    val busy = currentLoadingMessage != null || displayedLoadingMessage != null
    LaunchedEffect(currentLoadingMessage) {
        if (currentLoadingMessage != null) {
            loading.show(currentLoadingMessage)
        } else {
            loading.hide()
        }
    }
    var bulkActionsExpanded by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<ImportedFilterList?>(null) }
    var pendingEdit by remember { mutableStateOf<ImportedFilterList?>(null) }
    var pendingInfo by remember { mutableStateOf<ImportedFilterList?>(null) }
    var editName by remember { mutableStateOf("") }
    var editUrl by remember { mutableStateOf("") }
    var editText by remember { mutableStateOf<String?>(null) }
    var originalText by remember { mutableStateOf<String?>(null) }
    var editFileLoaded by remember { mutableStateOf(false) }
    var replacementUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }

    // Also cover unexpected navigation away from this destination. The WebView
    // gate will wait for this reload before displaying any page content.
    DisposableEffect(edits) {
        onDispose { edits.flush() }
    }

    fun update(
        affectsRules: Boolean = true,
        successMessage: String? = null,
        progressMessage: String = "Saving filter-list changes…",
        onStored: (() -> Unit)? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
        block: () -> Unit,
    ) {
        if (
            working ||
                loading.message.value != null ||
                blocker.status.value == BlockingStatus.LOADING
        ) {
            return
        }
        working = true
        workingMessage = progressMessage
        loading.show(progressMessage)
        error = null
        scope.launch {
            try {
                val savedLists =
                    withContext(Dispatchers.IO) {
                        block()
                        store.list()
                    }
                lists = savedLists
                if (affectsRules) {
                    edits.markChanged()
                }
                onStored?.let { stored ->
                    stored()
                    // The main indicator has just become visible after its dialog
                    // closed.
                    loading.show(
                        loading.message.value ?: progressMessage,
                        restartMinimum = true,
                    )
                }
                working = false
                loading.hide()
                loading.awaitHidden()
                onSuccess?.invoke()
                successMessage?.let { ToastManager.show(context, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                working = false
                loading.hide()
                loading.awaitHidden()
                val message = e.message ?: "Unable to change filter lists"
                if (onFailure != null) {
                    onFailure(message)
                } else {
                    error = message
                }
            } finally {
                working = false
            }
        }
    }

    fun leave(onLeave: () -> Unit) {
        if (
            working ||
                loading.message.value != null ||
                blocker.status.value == BlockingStatus.LOADING
        ) {
            return
        }
        val revision = edits.flush()
        if (revision == null) {
            onLeave()
            return
        }
        working = true
        workingMessage = "Applying filter rules…"
        loading.show(workingMessage)
        error = null
        scope.launch {
            try {
                blocker.awaitReload(revision)
                working = false
                loading.hide()
                loading.awaitHidden()
                onLeave()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Retry on the next exit attempt; keep the saved lists intact.
                edits.markChanged()
                error = e.message ?: "Unable to apply filter rules"
                working = false
                loading.hide()
            } finally {
                working = false
            }
        }
    }

    fun goBack() {
        if (!navController.popBackStack()) {
            navigateToWebViewScreen(navController)
        }
    }

    BackHandler(
        enabled =
            !showAddDialog &&
                pendingEdit == null &&
                pendingInfo == null &&
                pendingRemoval == null
    ) {
        leave(::goBack)
    }

    fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        null
                    } else {
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                OpenableColumns.DISPLAY_NAME
                            )
                        )
                    }
                }
        }
            .getOrNull() ?: "Imported filter list"

    val replaceFilePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            uri ->
            if (uri != null && pendingEdit?.type == FilterListType.FILE) {
                replacementUri = uri
            }
        }

    fun openEditor(item: ImportedFilterList) {
        if (item.type == FilterListType.PRESET) {
            return
        }
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
                    val contents =
                        withContext(Dispatchers.IO) {
                            store.editableFileText(item.id)
                        }
                    if (pendingEdit?.id == item.id) {
                        originalText = contents
                        editText = contents
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (pendingEdit?.id == item.id) {
                        error = e.message ?: "Cannot open filter list"
                    }
                } finally {
                    if (pendingEdit?.id == item.id) {
                        editFileLoaded = true
                    }
                }
            }
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 4.dp, start = 16.dp, end = 16.dp)
    ) {
        SettingLabel(
            navController,
            stringResource(R.string.settings_content_blocking_title),
            onBackClick = { leave(::goBack) },
            onExitClick = { leave { navigateToWebViewScreen(navController) } },
        )
        SettingDivider()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!blockingEnabled) {
                    item(key = "blocking-disabled") {
                        Text(
                            "Content blocking is disabled.",
                            modifier =
                                Modifier.fillMaxWidth().semantics {
                                    liveRegion = LiveRegionMode.Polite
                                },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                item(key = "actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            OutlinedButton(
                                onClick = { bulkActionsExpanded = true },
                                enabled = !busy && lists.isNotEmpty(),
                            ) {
                                Text(
                                    "Actions",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    painterResource(
                                        R.drawable.outline_keyboard_arrow_down_24
                                    ),
                                    contentDescription = null,
                                )
                            }
                            DropdownMenu(
                                expanded = bulkActionsExpanded,
                                onDismissRequest = { bulkActionsExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Enable all") },
                                    enabled = !busy && lists.any { !it.enabled },
                                    onClick = {
                                        bulkActionsExpanded = false
                                        update(
                                            successMessage = "All lists enabled"
                                        ) {
                                            store.setAllEnabled(true)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Disable all") },
                                    enabled = !busy && lists.any { it.enabled },
                                    onClick = {
                                        bulkActionsExpanded = false
                                        update(
                                            successMessage = "All lists disabled"
                                        ) {
                                            store.setAllEnabled(false)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh all") },
                                    enabled =
                                        !busy && lists.any { it.sourceUrl != null },
                                    onClick = {
                                        bulkActionsExpanded = false
                                        var summary =
                                            FilterRefreshResult(0, 0, emptyList())
                                        update(
                                            progressMessage =
                                                "Downloading and validating filter lists…",
                                            onSuccess = {
                                                ToastManager.show(
                                                    context,
                                                    "Refreshed ${summary.updated} " +
                                                        "of ${summary.total} lists",
                                                )
                                                if (summary.failures.isNotEmpty()) {
                                                    error =
                                                        summary.failures
                                                            .joinToString("\n") {
                                                                "${it.name}: ${it.message}"
                                                            }
                                                }
                                            },
                                        ) {
                                            summary = downloader.refreshAll(store)
                                        }
                                    },
                                )
                            }
                        }
                        Button(
                            onClick = {
                                error = null
                                addError = null
                                showAddDialog = true
                            },
                            enabled = !busy,
                        ) {
                            Text(
                                "Add list",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (!showAddDialog && pendingEdit == null) {
                    error?.let { message ->
                        item(key = "list-error") {
                            Text(
                                message,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (blockingStatus == BlockingStatus.ERROR) {
                    item(key = "engine-error") {
                        Text(
                            blockingError
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "Rule load failed: $it" }
                                ?: "Blocking unavailable",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                item(key = "list-heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Filter Lists",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${lists.size} total · ${lists.count { it.enabled }} enabled",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (lists.isEmpty()) {
                    item(key = "empty") { EmptyFilterListsCard() }
                }
                items(lists, key = { it.id }) { item ->
                    FilterListRow(
                        item = item,
                        busy = busy,
                        onEnabledChange = { enabled ->
                            update { store.setEnabled(item.id, enabled) }
                        },
                        onRefresh = {
                            update(
                                affectsRules = item.enabled,
                                successMessage = "List refreshed",
                                progressMessage =
                                    "Downloading and validating ${item.displayName()}…",
                            ) {
                                downloader.refresh(item, store)
                            }
                        },
                        onEdit = { openEditor(item) },
                        onInfo = { pendingInfo = item },
                        onClone = {
                            update(
                                affectsRules = false,
                                successMessage = "List cloned (disabled)",
                            ) {
                                store.cloneList(item.id)
                            }
                        },
                        onRemove = { pendingRemoval = item },
                    )
                }
            }
            if (busy) {
                Box(
                    modifier =
                        Modifier.matchParentSize()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach {
                                            it.consume()
                                        }
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier =
                            Modifier.padding(24.dp).semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Text(
                            currentLoadingMessage
                                ?: displayedLoadingMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    pendingInfo?.let { item ->
        FilterListInfoDialog(item, store, onDismiss = { pendingInfo = null })
    }

    pendingRemoval?.let { item ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    pendingRemoval = null
                }
            },
            title = { Text("Remove filter list?") },
            text = {
                Text(
                    item.displayName(),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        update { store.remove(item.id) }
                    },
                    enabled = !busy,
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }, enabled = !busy) {
                    Text("Cancel")
                }
            },
        )
    }

    pendingEdit?.let { item ->
        val changedFile =
            replacementUri != null || (originalText != editText && editText != null)
        val changedUrl = item.sourceUrl != null && editUrl.trim() != item.sourceUrl
        val duplicateUrl =
            item.type == FilterListType.URL &&
                changedUrl &&
                lists.hasUrlList(editUrl, item.id)
        val canSave =
            !busy &&
                editFileLoaded &&
                validListName(editName) &&
                !duplicateUrl &&
                item.type != FilterListType.PRESET &&
                (item.type == FilterListType.FILE ||
                    editUrl.toFilterListUrlOrNull() != null) &&
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
            error =
                if (duplicateUrl) {
                    "A URL list with this address already exists"
                } else {
                    error
                },
            busy = busy,
            canSave = canSave,
            onDismiss = {
                if (!busy) {
                    pendingEdit = null
                }
            },
            onSave = {
                val name = editName.trim()
                val url = editUrl.trim()
                val sourceUri = replacementUri
                val typedContent =
                    if (originalText != editText && editText != null) {
                        editText
                    } else {
                        null
                    }
                val affectsRules =
                    item.enabled &&
                        when (item.type) {
                            FilterListType.FILE -> changedFile
                            FilterListType.URL -> changedUrl
                            FilterListType.PRESET -> false
                        }
                update(
                    affectsRules = affectsRules,
                    successMessage = "List saved",
                    progressMessage = "Validating and saving the filter list…",
                    onStored = { pendingEdit = null },
                ) {
                    when (item.type) {
                        FilterListType.FILE -> {
                            val input =
                                when {
                                    sourceUri != null ->
                                        context.contentResolver.openInputStream(
                                            sourceUri
                                        )
                                            ?: throw IOException(
                                                "Cannot open the selected file"
                                            )

                                    typedContent != null ->
                                        typedContent.byteInputStream(Charsets.UTF_8)

                                    else -> null
                                }
                            store.updateFile(item.id, name, input)
                        }

                        FilterListType.URL ->
                            downloader.editUrl(item, name, url, store)

                        FilterListType.PRESET ->
                            throw IllegalArgumentException(
                                "Preset lists are read-only"
                            )
                    }
                }
            },
        )
    }

    if (showAddDialog) {
        AddFilterListDialog(
            lists = lists,
            busy = busy,
            progressMessage =
                currentLoadingMessage ?: displayedLoadingMessage.orEmpty(),
            error = addError,
            onClearError = { addError = null },
            onDismiss = {
                if (!busy) {
                    showAddDialog = false
                    addError = null
                }
            },
            onAddCustom = { name, text, uri ->
                update(
                    successMessage = "List added",
                    progressMessage = "Reading and validating the filter list…",
                    onStored = { showAddDialog = false },
                    onFailure = { addError = it },
                ) {
                    val input =
                        if (uri != null) {
                            context.contentResolver.openInputStream(uri)
                                ?: throw IOException("Cannot open the selected file")
                        } else {
                            text.byteInputStream(Charsets.UTF_8)
                        }
                    store.importFrom(name, input)
                }
            },
            onDownloadUrl = { url, name ->
                update(
                    successMessage = "List added",
                    progressMessage = "Downloading and validating the filter list…",
                    onStored = { showAddDialog = false },
                    onFailure = { addError = it },
                ) {
                    downloader.downloadUrl(url, name, store)
                }
            },
            onDownloadPreset = { suggestion ->
                update(
                    successMessage = "List added",
                    progressMessage =
                        "Downloading and validating ${suggestion.name}…",
                    onStored = { showAddDialog = false },
                    onFailure = { addError = it },
                ) {
                    downloader.download(suggestion, store)
                }
            },
        )
    }
}

@Composable
private fun EmptyFilterListsCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
    ) {
        Text(
            "No filter lists have been added",
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FilterListInfoDialog(
    item: ImportedFilterList,
    store: FilterListStore,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val numbers = remember { NumberFormat.getIntegerInstance() }
    var fileSize by remember(item.id) { mutableStateOf<Long?>(null) }
    var info by remember(item.id) { mutableStateOf<FilterListInfo?>(null) }
    var failure by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(store, item.id) {
        try {
            fileSize = withContext(Dispatchers.IO) { store.fileSizeBytes(item.id) }
            info = withContext(Dispatchers.IO) { store.info(item.id) }
            fileSize = info?.sizeBytes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e.message ?: "Unable to read filter list info"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SelectionContainer {
                Text(
                    item.displayName(),
                    modifier =
                        Modifier.heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState()),
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val details = info
                val unavailable = failure != null
                InfoSection("List details") {
                    InfoStat(
                        "Type",
                        when (item.type) {
                            FilterListType.FILE -> "Custom"
                            FilterListType.URL -> "URL"
                            FilterListType.PRESET -> "Preset"
                        },
                    )
                    InfoStat(
                        "Status",
                        if (item.enabled) {
                            "Enabled"
                        } else {
                            "Disabled"
                        },
                    )
                    InfoStat(
                        "Last refreshed",
                        when {
                            item.type == FilterListType.FILE -> "Not applicable"

                            item.lastRefreshedAt == null -> "Unknown"

                            else ->
                                remember(item.lastRefreshedAt) {
                                    DateFormat.getDateTimeInstance(
                                            DateFormat.MEDIUM,
                                            DateFormat.SHORT,
                                        )
                                        .format(Date(item.lastRefreshedAt))
                                }
                        },
                        stacked = true,
                    )
                    InfoStat(
                        "File size",
                        fileSize?.let { Formatter.formatShortFileSize(context, it) },
                        unavailable,
                    )
                }
                InfoSection("Rules") {
                    InfoStat(
                        "Network",
                        details?.let { numbers.format(it.networkRules) },
                        unavailable,
                    )
                    InfoStat(
                        "Cosmetic",
                        details?.let { numbers.format(it.cosmeticRules) },
                        unavailable,
                    )
                    InfoStat(
                        "Skipped",
                        details?.let { numbers.format(it.skippedRules) },
                        unavailable,
                    )
                    InfoStat(
                        "Invalid",
                        details?.let { numbers.format(it.invalidRules) },
                        unavailable,
                    )
                    InfoStat(
                        "Total",
                        details?.let { numbers.format(it.totalRules) },
                        unavailable,
                    )
                }
                item.sourceUrl?.let { source ->
                    InfoSection("Source URL") {
                        SelectionContainer {
                            Text(
                                source,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun InfoSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun InfoStat(
    label: String,
    value: String?,
    unavailable: Boolean = false,
    stacked: Boolean = false,
) {
    Column {
        if (stacked) {
            Text(
                label,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!stacked) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier.weight(1.4f),
                contentAlignment =
                    if (stacked) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
            ) {
                if (value != null || unavailable) {
                    Text(
                        value ?: "Unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign =
                            if (stacked) {
                                TextAlign.Start
                            } else {
                                TextAlign.End
                            },
                    )
                } else {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(16.dp).semantics {
                                contentDescription = "Loading $label"
                            },
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun FilterListRow(
    item: ImportedFilterList,
    busy: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    onClone: () -> Unit,
    onRemove: () -> Unit,
) {
    val name = item.displayName()
    var menuExpanded by remember { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(
                checked = item.enabled,
                enabled = !busy,
                onCheckedChange = onEnabledChange,
                modifier =
                    Modifier.semantics { contentDescription = "Filter $name" },
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(
                            when (item.type) {
                                FilterListType.FILE ->
                                    R.drawable.outline_description_24
                                FilterListType.URL -> R.drawable.outline_link_24
                                FilterListType.PRESET ->
                                    R.drawable.outline_checklist_24
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        when (item.type) {
                            FilterListType.FILE -> "Custom"
                            FilterListType.URL -> "URL"
                            FilterListType.PRESET -> "Preset"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = !busy) {
                    Icon(
                        painterResource(R.drawable.outline_more_vert_24),
                        contentDescription = "Options for $name",
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (item.type != FilterListType.PRESET) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.baseline_edit_24),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            enabled = !busy,
                        )
                    }
                    if (item.sourceUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.baseline_refresh_24),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRefresh()
                            },
                            enabled = !busy,
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Info") },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.baseline_info_24),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onInfo()
                        },
                        enabled = !busy,
                    )
                    if (item.type == FilterListType.FILE) {
                        DropdownMenuItem(
                            text = { Text("Clone") },
                            leadingIcon = {
                                Icon(
                                    painterResource(
                                        R.drawable.baseline_content_copy_24
                                    ),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onClone()
                            },
                            enabled = !busy,
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.baseline_delete_24),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                        enabled = !busy,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListTypeDropdown(
    value: FilterListType,
    onChange: (FilterListType) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val label =
        when (value) {
            FilterListType.FILE -> "Custom"
            FilterListType.URL -> "URL"
            FilterListType.PRESET -> "Preset"
        }
    Box(
        modifier = Modifier.fillMaxWidth().onSizeChanged { anchorWidth = it.width }
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier.fillMaxWidth().semantics {
                    contentDescription = "List type"
                },
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(R.drawable.outline_keyboard_arrow_down_24),
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(density) { anchorWidth.toDp() }),
        ) {
            listOf(FilterListType.PRESET, FilterListType.URL, FilterListType.FILE)
                .forEach { type ->
                    DropdownMenuItem(
                        modifier = Modifier.fillMaxWidth(),
                        text = {
                            Text(
                                when (type) {
                                    FilterListType.FILE -> "Custom"
                                    FilterListType.URL -> "URL"
                                    FilterListType.PRESET -> "Preset"
                                }
                            )
                        },
                        onClick = {
                            expanded = false
                            onChange(type)
                        },
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
    progressMessage: String,
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onAddCustom: (String, String, Uri?) -> Unit,
    onDownloadUrl: (String, String) -> Unit,
    onDownloadPreset: (SuggestedFilterList) -> Unit,
) {
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf(FilterListType.PRESET) }
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Uploaded files stay as URIs. Never read their contents into the text field.
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf("Selected file") }
    var text by rememberSaveable { mutableStateOf("") }
    var textError by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestion by remember { mutableStateOf(SuggestedFilterLists.all.first()) }
    var presetsExpanded by remember { mutableStateOf(false) }
    var presetDropdownWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val installed = lists.any {
        it.type == FilterListType.PRESET && it.sourceUrl == suggestion.url
    }
    val duplicateUrl = lists.hasUrlList(url)
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            uri ->
            if (uri != null) {
                selectedFile = uri.toString()
                textError = null
                onClearError()
            }
        }
    LaunchedEffect(selectedFile) {
        selectedFileName = "Selected file"
        val uri = selectedFile?.let(Uri::parse) ?: return@LaunchedEffect
        selectedFileName =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null,
                        )
                        ?.use { cursor ->
                            if (!cursor.moveToFirst()) {
                                null
                            } else {
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow(
                                        OpenableColumns.DISPLAY_NAME
                                    )
                                )
                            }
                        }
                }
                    .getOrNull() ?: "Selected file"
            }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add filter list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier =
                        Modifier.weight(1f, fill = false)
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterListField("List type") {
                        ListTypeDropdown(
                            value = type,
                            onChange = {
                                type = it
                                onClearError()
                            },
                            enabled = !busy,
                        )
                    }
                    if (type != FilterListType.PRESET) {
                        FilterListField("Name") {
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    onClearError()
                                },
                                modifier =
                                    Modifier.fillMaxWidth().semantics {
                                        contentDescription = "Name"
                                    },
                                placeholder = { Text("e.g. My filters") },
                                singleLine = true,
                                enabled = !busy,
                                isError = name.isNotEmpty() && !validListName(name),
                            )
                        }
                    }
                    when (type) {
                        FilterListType.FILE -> {
                            if (selectedFile == null) {
                                FilterListField("Filter rules") {
                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = { value ->
                                            if (
                                                value.length <=
                                                    FilterListStore
                                                        .MAX_INLINE_EDIT_BYTES &&
                                                    value
                                                        .toByteArray(Charsets.UTF_8)
                                                        .size <=
                                                        FilterListStore
                                                            .MAX_INLINE_EDIT_BYTES
                                            ) {
                                                text = value
                                                textError = null
                                                onClearError()
                                            } else {
                                                textError =
                                                    "Text is too long to edit " +
                                                        "here (128 KiB max). " +
                                                        "Import a file instead."
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = {
                                            Text("Type or paste filter rules")
                                        },
                                        minLines = 5,
                                        maxLines = 8,
                                        enabled = !busy,
                                        isError = textError != null,
                                        supportingText =
                                            textError?.let { message ->
                                                ({ Text(message) })
                                            },
                                    )
                                }
                            } else {
                                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement =
                                            Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            "Selected file",
                                            style =
                                                MaterialTheme.typography.labelMedium,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .onSurfaceVariant,
                                        )
                                        Text(
                                            selectedFileName,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        AuthenticationManager.bypassAuthForWindow(
                                            60_000
                                        )
                                        picker.launch(arrayOf("*/*"))
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        painterResource(
                                            R.drawable.outline_upload_file_24
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (selectedFile == null) {
                                            "Import file"
                                        } else {
                                            "Change file"
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (selectedFile != null) {
                                    TextButton(
                                        onClick = {
                                            selectedFile = null
                                            onClearError()
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            "Use text",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        FilterListType.URL -> {
                            FilterListField("URL") {
                                OutlinedTextField(
                                    value = url,
                                    onValueChange = {
                                        url = it
                                        onClearError()
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth().semantics {
                                            contentDescription = "URL"
                                        },
                                    singleLine = true,
                                    enabled = !busy,
                                    isError = duplicateUrl,
                                    supportingText =
                                        if (duplicateUrl) {
                                            ({
                                                Text(
                                                    "This address is already " +
                                                        "in a URL list"
                                                )
                                            })
                                        } else {
                                            null
                                        },
                                )
                            }
                        }

                        FilterListType.PRESET -> {
                            FilterListField("Preset") {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth().onSizeChanged {
                                            presetDropdownWidth = it.width
                                        }
                                ) {
                                    OutlinedButton(
                                        onClick = { presetsExpanded = true },
                                        enabled = !busy,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier =
                                            Modifier.fillMaxWidth().semantics {
                                                contentDescription = "Preset"
                                            },
                                    ) {
                                        Text(
                                            suggestion.name,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Icon(
                                            painterResource(
                                                R.drawable
                                                    .outline_keyboard_arrow_down_24
                                            ),
                                            contentDescription = null,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = presetsExpanded,
                                        onDismissRequest = {
                                            presetsExpanded = false
                                        },
                                        modifier =
                                            Modifier.width(
                                                with(density) {
                                                    presetDropdownWidth.toDp()
                                                }
                                            ),
                                    ) {
                                        SuggestedFilterLists.all.forEach { preset ->
                                            DropdownMenuItem(
                                                modifier = Modifier.fillMaxWidth(),
                                                text = {
                                                    Text(
                                                        preset.name,
                                                        maxLines = 1,
                                                        overflow =
                                                            TextOverflow.Ellipsis,
                                                    )
                                                },
                                                onClick = {
                                                    suggestion = preset
                                                    presetsExpanded = false
                                                    onClearError()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            Surface(
                                color =
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Column(
                                    modifier =
                                        Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(
                                        verticalArrangement =
                                            Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            "Category",
                                            style =
                                                MaterialTheme.typography.labelSmall,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .onSurfaceVariant,
                                        )
                                        Text(
                                            suggestion.description,
                                            style =
                                                MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Column(
                                        verticalArrangement =
                                            Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            "Source URL",
                                            style =
                                                MaterialTheme.typography.labelSmall,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .onSurfaceVariant,
                                        )
                                        SelectionContainer {
                                            Text(
                                                suggestion.url,
                                                style =
                                                    MaterialTheme.typography
                                                        .bodySmall,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (busy) {
                        Text(
                            progressMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                if (type == FilterListType.PRESET && installed) {
                    Text(
                        "Already added",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (type) {
                        FilterListType.FILE ->
                            onAddCustom(
                                name.trim(),
                                text,
                                selectedFile?.let(Uri::parse),
                            )

                        FilterListType.URL -> onDownloadUrl(url.trim(), name.trim())

                        FilterListType.PRESET -> onDownloadPreset(suggestion)
                    }
                },
                enabled =
                    !busy &&
                        when (type) {
                            FilterListType.FILE ->
                                validListName(name) &&
                                    (selectedFile != null ||
                                        (text.isNotBlank() && textError == null))

                            FilterListType.URL ->
                                url.toFilterListUrlOrNull() != null &&
                                    validListName(name) &&
                                    !duplicateUrl

                            FilterListType.PRESET -> !installed
                        },
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (type == FilterListType.FILE) {
                        "Add"
                    } else {
                        "Download"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit filter list") },
        text = {
            Column(
                modifier =
                    Modifier.heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    isError = !validListName(name),
                )
                if (item.type != FilterListType.FILE) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        label = { Text("URL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        maxLines = 3,
                    )
                } else if (!fileLoaded) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    if (fileText != null) {
                        OutlinedTextField(
                            value = fileText,
                            onValueChange = onFileTextChange,
                            label = { Text("Filter rules") },
                            modifier =
                                Modifier.fillMaxWidth().heightIn(min = 130.dp),
                            minLines = 5,
                            maxLines = 10,
                            enabled = !busy && replacementName == null,
                        )
                    } else {
                        Text(
                            "This list is too large to edit inline. " +
                                "Choose a replacement text file instead.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onPickReplacement,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            if (replacementName == null) {
                                "Replace from file"
                            } else {
                                "Change replacement file"
                            }
                        )
                    }
                    if (replacementName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                replacementName,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = onClearReplacement,
                                enabled = !busy,
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}
