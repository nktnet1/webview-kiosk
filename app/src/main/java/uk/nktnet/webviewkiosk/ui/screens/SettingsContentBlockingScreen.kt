package uk.nktnet.webviewkiosk.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableColumn
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.blocking.BlockingStatus
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
import uk.nktnet.webviewkiosk.blocking.FilterListBatchAction
import uk.nktnet.webviewkiosk.blocking.FilterListInfo
import uk.nktnet.webviewkiosk.blocking.FilterListStore
import uk.nktnet.webviewkiosk.blocking.FilterListType
import uk.nktnet.webviewkiosk.blocking.FilterRefreshResult
import uk.nktnet.webviewkiosk.blocking.ImportedFilterList
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterList
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterListDownloader
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterLists
import uk.nktnet.webviewkiosk.blocking.feedback
import uk.nktnet.webviewkiosk.blocking.filterListFailureMessage
import uk.nktnet.webviewkiosk.blocking.toFilterListUrlOrNull
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeDialog
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
    val downloader = remember { SuggestedFilterListDownloader() }
    val scope = rememberCoroutineScope()
    val blockingEnabled by blocker.enabled.collectAsState()
    val blockingStatus by blocker.status.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val mutationMutex = remember { Mutex() }
    var lists by remember { mutableStateOf(store.list()) }
    var savingSwitchIds by remember { mutableStateOf(emptySet<String>()) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val missingDefaults = SuggestedFilterLists.missingDefaults(lists)
    var pendingDefaults by remember {
        mutableStateOf(emptyList<SuggestedFilterList>())
    }
    var working by remember { mutableStateOf(false) }
    var workingMessage by remember { mutableStateOf<String?>(null) }
    val busy = working
    var bulkActionsExpanded by remember { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var selectedIds by
        rememberSaveable(
            stateSaver =
                listSaver<Set<String>, String>(
                    save = { it.toList() },
                    restore = { it.toSet() },
                )
        ) {
            mutableStateOf(emptySet())
        }
    val selectedLists = lists.filter { it.id in selectedIds }
    var pendingRemoval by remember {
        mutableStateOf(emptyList<ImportedFilterList>())
    }
    var pendingEdit by remember { mutableStateOf<ImportedFilterList?>(null) }
    var pendingInfo by remember { mutableStateOf<ImportedFilterList?>(null) }
    var editName by remember { mutableStateOf("") }
    var editUrl by remember { mutableStateOf("") }
    var editText by remember { mutableStateOf<String?>(null) }
    var originalText by remember { mutableStateOf<String?>(null) }
    var editFileLoaded by remember { mutableStateOf(false) }
    var replacementUri by remember { mutableStateOf<Uri?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }

    fun notify(message: String, retry: (() -> Unit)? = null) {
        if (retry == null) {
            snackbar.currentSnackbarData?.dismiss()
            ToastManager.show(context, message)
            return
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result =
                snackbar.showSnackbar(
                    message = message,
                    actionLabel = "Retry",
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                retry()
            }
        }
    }

    fun reportBatch(
        summary: FilterRefreshResult,
        action: FilterListBatchAction,
        retry: () -> Unit,
    ) {
        summary.failures.forEach { failure ->
            Log.w("ContentBlocking", "${failure.name}: ${failure.message}")
        }
        notify(
            summary.feedback(action),
            retry.takeIf { summary.failures.isNotEmpty() },
        )
    }

    fun update(
        affectsRules: Boolean = true,
        successMessage: String? = null,
        progressMessage: String? = null,
        onStored: (() -> Unit)? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
        block: () -> Unit,
    ) {
        if (working) {
            return
        }
        working = true
        workingMessage = progressMessage
        editError = null
        scope.launch {
            try {
                val savedLists = mutationMutex.withLock {
                    withContext(Dispatchers.IO) {
                        block()
                        if (affectsRules) {
                            blocker.markRulesChanged()
                        }
                        store.list()
                    }
                }
                lists = savedLists
                selectedIds = selectedIds.intersect(savedLists.map { it.id }.toSet())
                onStored?.invoke()
                working = false
                workingMessage = null
                onSuccess?.invoke()
                successMessage?.let { notify(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ContentBlocking", "Filter list update failed", e)
                val message = filterListFailureMessage(e)
                if (onFailure != null) {
                    onFailure(message)
                } else {
                    notify(message)
                }
            } finally {
                working = false
                workingMessage = null
            }
        }
    }

    fun toggleList(item: ImportedFilterList, enabled: Boolean) {
        if (working || item.id in savingSwitchIds) {
            return
        }
        savingSwitchIds += item.id
        lists = lists.map {
            if (it.id == item.id) {
                it.copy(enabled = enabled)
            } else {
                it
            }
        }
        scope.launch {
            try {
                mutationMutex.withLock {
                    try {
                        withContext(Dispatchers.IO) {
                            store.setEnabled(item.id, enabled)
                            blocker.markRulesChanged()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lists = lists.map {
                            if (it.id == item.id) {
                                it.copy(enabled = item.enabled)
                            } else {
                                it
                            }
                        }
                        Log.w("ContentBlocking", "Cannot toggle filter list", e)
                        notify(filterListFailureMessage(e))
                    }
                }
            } finally {
                savingSwitchIds -= item.id
            }
        }
    }

    fun downloadDefaults(requested: List<SuggestedFilterList>) {
        var summary = FilterRefreshResult(0, 0, emptyList())
        update(
            progressMessage = "ÎDownloading and validating uBlock Origin defaults…",
            onSuccess = {
                reportBatch(summary, FilterListBatchAction.ADD_DEFAULTS) {
                    downloadDefaults(requested)
                }
            },
        ) {
            summary = downloader.downloadDefaults(store, requested)
        }
    }

    fun refreshAll() {
        var summary = FilterRefreshResult(0, 0, emptyList())
        update(
            progressMessage = "Downloading and validating filter lists…",
            onSuccess = {
                reportBatch(summary, FilterListBatchAction.REFRESH) { refreshAll() }
            },
        ) {
            summary = downloader.refreshAll(store)
        }
    }

    fun refreshSelected(ids: Set<String>) {
        var summary = FilterRefreshResult(0, 0, emptyList())
        update(
            affectsRules =
                lists.any {
                    it.id in ids && it.enabled && it.sourceUrl != null
                },
            progressMessage = "Downloading and validating selected filter lists…",
            onSuccess = {
                reportBatch(summary, FilterListBatchAction.REFRESH) {
                    refreshSelected(ids)
                }
            },
        ) {
            summary = downloader.refreshSelected(store, ids)
        }
    }

    fun finishManaging() {
        managing = false
        selectedIds = emptySet()
        bulkActionsExpanded = false
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
                pendingRemoval.isEmpty()
    ) {
        if (!working) {
            if (managing) {
                finishManaging()
            } else {
                goBack()
            }
        }
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
        editError = null
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
                        Log.w("ContentBlocking", "Cannot open filter list", e)
                        editError = "Couldn't open this list. Please try again."
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
            onBackClick = {
                if (!working) {
                    if (managing) {
                        finishManaging()
                    } else {
                        goBack()
                    }
                }
            },
            onExitClick = {
                if (!working) {
                    navigateToWebViewScreen(navController)
                }
            },
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
                            "Content blocking is disabled. " +
                                "The settings below will not take effect.",
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .semantics {
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
                                enabled =
                                    !busy &&
                                        (!managing || selectedLists.isNotEmpty()),
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
                                if (managing) {
                                    DropdownMenuItem(
                                        text = { Text("Enable selected") },
                                        enabled =
                                            !busy &&
                                                selectedLists.any { !it.enabled },
                                        onClick = {
                                            bulkActionsExpanded = false
                                            val ids =
                                                selectedLists.map { it.id }.toSet()
                                            update(
                                                successMessage =
                                                    "Selected lists enabled"
                                            ) {
                                                store.setEnabled(ids, true)
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Disable selected") },
                                        enabled =
                                            !busy &&
                                                selectedLists.any { it.enabled },
                                        onClick = {
                                            bulkActionsExpanded = false
                                            val ids =
                                                selectedLists.map { it.id }.toSet()
                                            update(
                                                successMessage =
                                                    "Selected lists disabled"
                                            ) {
                                                store.setEnabled(ids, false)
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Update selected") },
                                        enabled =
                                            !busy &&
                                                selectedLists.any {
                                                    it.sourceUrl != null
                                                },
                                        onClick = {
                                            bulkActionsExpanded = false
                                            refreshSelected(
                                                selectedLists.map { it.id }.toSet()
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Remove selected",
                                                color =
                                                    MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        enabled =
                                            !busy && selectedLists.isNotEmpty(),
                                        onClick = {
                                            bulkActionsExpanded = false
                                            pendingRemoval = selectedLists
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Manage") },
                                        enabled = !busy && lists.isNotEmpty(),
                                        onClick = {
                                            bulkActionsExpanded = false
                                            selectedIds = emptySet()
                                            managing = true
                                        },
                                    )
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
                                            !busy &&
                                                lists.any { it.sourceUrl != null },
                                        onClick = {
                                            bulkActionsExpanded = false
                                            refreshAll()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Add Default") },
                                        enabled =
                                            !busy && missingDefaults.isNotEmpty(),
                                        onClick = {
                                            bulkActionsExpanded = false
                                            pendingDefaults = missingDefaults
                                        },
                                    )
                                }
                            }
                        }
                        if (managing) {
                            Button(
                                onClick = { finishManaging() },
                                enabled = !busy,
                            ) {
                                Text("Done")
                            }
                        } else {
                            Button(
                                onClick = {
                                    editError = null
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
                }
                if (blockingStatus == BlockingStatus.ERROR) {
                    item(key = "blocking-status") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Blocking couldn't be updated.",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { blocker.reload() },
                                enabled = !busy,
                            ) {
                                Text("Retry")
                            }
                        }
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
                            "${lists.count { it.enabled }}/${lists.size} enabled",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (managing) {
                    item(key = "selection") {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TriStateCheckbox(
                                    state =
                                        when {
                                            selectedLists.isEmpty() ->
                                                ToggleableState.Off
                                            selectedLists.size == lists.size ->
                                                ToggleableState.On
                                            else -> ToggleableState.Indeterminate
                                        },
                                    enabled = !busy && lists.isNotEmpty(),
                                    onClick = {
                                        selectedIds =
                                            if (selectedLists.size == lists.size) {
                                                emptySet()
                                            } else {
                                                lists.map { it.id }.toSet()
                                            }
                                    },
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription =
                                                "Select all filter lists"
                                        },
                                )
                                Text(
                                    "${selectedLists.size} selected",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                if (lists.isEmpty()) {
                    item(key = "empty") {
                        EmptyFilterListsCard(
                            enabled = !busy,
                            onAddDefault = { pendingDefaults = missingDefaults },
                        )
                    }
                }
                if (lists.isNotEmpty()) {
                    item(key = "filter-list-items") {
                        ReorderableColumn(
                            list = lists,
                            onSettle = { from, to ->
                                if (managing && !busy && from != to) {
                                    val reordered =
                                        lists.toMutableList().apply {
                                            add(to, removeAt(from))
                                        }
                                    val affectsRules =
                                        lists.filter { it.enabled }.map { it.id } !=
                                            reordered
                                                .filter { it.enabled }
                                                .map { it.id }
                                    lists = reordered
                                    update(
                                        affectsRules = affectsRules,
                                        onFailure = { message ->
                                            lists = store.list()
                                            notify(message)
                                        },
                                    ) {
                                        store.reorder(reordered.map { it.id })
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) { _, item, _ ->
                            key(item.id) {
                                ReorderableItem {
                                    FilterListRow(
                                        item = item,
                                        busy = busy || item.id in savingSwitchIds,
                                        managing = managing,
                                        selected = item.id in selectedIds,
                                        onSelectedChange = { selected ->
                                            selectedIds =
                                                if (selected) {
                                                    selectedIds + item.id
                                                } else {
                                                    selectedIds - item.id
                                                }
                                        },
                                        onEnabledChange = { enabled ->
                                            toggleList(item, enabled)
                                        },
                                        onRefresh = {
                                            update(
                                                affectsRules = item.enabled,
                                                successMessage = "List refreshed",
                                                progressMessage =
                                                    "Downloading and validating " +
                                                        "${item.displayName()}…",
                                            ) {
                                                downloader.refresh(item, store)
                                            }
                                        },
                                        onEdit = { openEditor(item) },
                                        onInfo = { pendingInfo = item },
                                        onClone = {
                                            update(
                                                affectsRules = false,
                                                successMessage =
                                                    "List cloned (disabled)",
                                            ) {
                                                store.cloneList(item.id)
                                            }
                                        },
                                        onRemove = { pendingRemoval = listOf(item) },
                                        dragHandle = {
                                            Box(
                                                modifier =
                                                    Modifier.size(48.dp)
                                                        .then(
                                                            if (busy || !managing) {
                                                                Modifier
                                                            } else {
                                                                Modifier
                                                                    .draggableHandle()
                                                            }
                                                        ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    painterResource(
                                                        R.drawable.drag_indicator_24
                                                    ),
                                                    contentDescription =
                                                        "Reorder ${item.displayName()}",
                                                    tint =
                                                        MaterialTheme.colorScheme
                                                            .onSurfaceVariant,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (working && workingMessage != null) {
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
                            workingMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier =
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            ) { data ->
                Snackbar(
                    data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionContentColor = MaterialTheme.colorScheme.primary,
                    dismissActionContentColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    pendingInfo?.let { item ->
        FilterListInfoDialog(item, store, onDismiss = { pendingInfo = null })
    }

    if (pendingDefaults.isNotEmpty()) {
        val adding = pendingDefaults
        AlertDialog(
            onDismissRequest = { pendingDefaults = emptyList() },
            title = { Text("Add default lists") },
            text = {
                Column(
                    modifier =
                        Modifier.heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("The following lists will be added:")
                    adding.forEach { preset -> FilterListBullet(preset.name) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDefaults = emptyList()
                        downloadDefaults(adding)
                    },
                    enabled = !busy,
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDefaults = emptyList() }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (pendingRemoval.isNotEmpty()) {
        val removing = pendingRemoval
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    pendingRemoval = emptyList()
                }
            },
            title = {
                Text(
                    if (removing.size == 1) {
                        "Remove filter list?"
                    } else {
                        "Remove ${removing.size} filter lists?"
                    }
                )
            },
            text = {
                Column(
                    modifier =
                        Modifier.heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    removing.forEach { item ->
                        FilterListBullet(item.displayName())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = emptyList()
                        update(affectsRules = removing.any { it.enabled }) {
                            store.remove(removing.map { it.id }.toSet())
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRemoval = emptyList() },
                    enabled = !busy,
                ) {
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
                    editError
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
                    onFailure = { editError = it },
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
            progressMessage = workingMessage.orEmpty(),
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
private fun FilterListBullet(name: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmptyFilterListsCard(enabled: Boolean, onAddDefault: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "No filter lists have been added",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAddDefault, enabled = enabled) { Text("Add Default") }
        }
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
    val scrollState = rememberScrollState()
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
            Log.w("ContentBlocking", "Cannot read filter list info", e)
            failure = "List information is unavailable. Please try again."
        }
    }
    SystemSafeDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier =
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(vertical = 16.dp)
                    .fillMaxHeight(0.9f)
                    .wrapContentHeight()
                    .widthIn(min = 280.dp, max = 560.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)
            ) {
                SelectionContainer {
                    Text(
                        item.displayName(),
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .heightIn(max = 120.dp)
                                .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 540.dp)
                            .padding(horizontal = 24.dp)
                ) {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth().verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val details = info
                        val unavailable = failure != null
                        item.sourceUrl?.let { source ->
                            InfoSection("Source URL") {
                                SelectionContainer {
                                    Text(
                                        source,
                                        modifier =
                                            Modifier.fillMaxWidth().padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
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
                                "File size",
                                fileSize?.let {
                                    Formatter.formatShortFileSize(context, it)
                                },
                                unavailable,
                            )
                            InfoStat(
                                "Last updated",
                                when {
                                    item.type == FilterListType.FILE ->
                                        "Not applicable"

                                    item.lastRefreshedAt == null -> "Unknown"

                                    else ->
                                        remember(item.lastRefreshedAt) {
                                            SimpleDateFormat(
                                                    "d MMM yyyy, h:mm a",
                                                    Locale.getDefault(),
                                                )
                                                .format(Date(item.lastRefreshedAt))
                                        }
                                },
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
                                "Scriptlets",
                                details?.let { numbers.format(it.scriptletRules) },
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
                        details
                            ?.takeIf { it.partialRules > 0 }
                            ?.let { info ->
                                InfoSection("Partially supported rules") {
                                    InfoStat(
                                        "\$all",
                                        numbers.format(info.partialRules),
                                    )
                                    Text(
                                        "Applies to intercepted network requests and page navigation. " +
                                            "Popup and inline script/font filtering are unavailable.",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        details
                            ?.skipReasons
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { reasons ->
                                InfoSection("Skipped rules") {
                                    reasons.entries
                                        .sortedBy { it.key.ordinal }
                                        .forEach { (reason, count) ->
                                            InfoStat(
                                                reason.label,
                                                numbers.format(count),
                                            )
                                        }
                                }
                            }
                        failure?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (scrollState.canScrollForward) {
                        Box(
                            modifier =
                                Modifier.align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                AlertDialogDefaults.containerColor
                                                    .copy(alpha = 0f),
                                                AlertDialogDefaults.containerColor
                                                    .copy(alpha = 0.7f),
                                            )
                                        )
                                    ),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Text(
                                "Scroll for more \u2193",
                                modifier = Modifier.padding(bottom = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.9f
                                    ),
                            )
                        }
                    }
                }
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(top = 4.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
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
    singleLine: Boolean = false,
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
                        maxLines =
                            if (singleLine) {
                                1
                            } else {
                                Int.MAX_VALUE
                            },
                        overflow = TextOverflow.Ellipsis,
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
    managing: Boolean,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    onClone: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (managing) {
                Checkbox(
                    checked = selected,
                    enabled = !busy,
                    onCheckedChange = onSelectedChange,
                    modifier =
                        Modifier.semantics { contentDescription = "Select $name" },
                )
            } else {
                Switch(
                    checked = item.enabled,
                    enabled = !busy,
                    onCheckedChange = onEnabledChange,
                    modifier =
                        Modifier.semantics { contentDescription = "Filter $name" },
                )
            }
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
                    val type =
                        when (item.type) {
                            FilterListType.FILE -> "Custom"
                            FilterListType.URL -> "URL"
                            FilterListType.PRESET -> "Preset"
                        }
                    val status =
                        if (item.enabled) {
                            "Enabled"
                        } else {
                            "Disabled"
                        }
                    Text(
                        if (managing) {
                            "$type · $status"
                        } else {
                            type
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (managing) {
                dragHandle()
            } else {
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
                                        painterResource(
                                            R.drawable.baseline_refresh_24
                                        ),
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
