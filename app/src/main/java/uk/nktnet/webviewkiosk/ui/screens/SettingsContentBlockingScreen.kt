package uk.nktnet.webviewkiosk.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
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
import uk.nktnet.webviewkiosk.blocking.FilterListBatchProgress
import uk.nktnet.webviewkiosk.blocking.FilterListDownloadProgress
import uk.nktnet.webviewkiosk.blocking.FilterListDownloadStage
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
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes
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
    var downloadProgress by remember {
        mutableStateOf<FilterListBatchProgress?>(null)
    }
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
                    actionLabel = context.getString(R.string.ui_retry),
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
        initialProgress: FilterListBatchProgress? = null,
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
        downloadProgress = initialProgress
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
                downloadProgress = null
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
                downloadProgress = null
            }
        }
    }

    fun startingProgress(name: String, total: Int = 1) =
        FilterListBatchProgress(
            name = name,
            current = 1,
            total = total,
            completed = 0,
            download =
                FilterListDownloadProgress(FilterListDownloadStage.CONNECTING),
        )

    fun singleDownloadProgress(name: String): (FilterListDownloadProgress) -> Unit =
        { progress ->
            downloadProgress =
                FilterListBatchProgress(
                    name = name,
                    current = 1,
                    total = 1,
                    completed = 0,
                    download = progress,
                )
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
        val pending = requested.filterNot { preset ->
            lists.any { it.sourceUrl == preset.url }
        }
        update(
            progressMessage =
                context.getString(R.string.ui_downloading_ublock_origin_defaults),
            initialProgress =
                pending.firstOrNull()?.let {
                    startingProgress(it.name, pending.size)
                },
            onSuccess = {
                reportBatch(summary, FilterListBatchAction.ADD_DEFAULTS) {
                    downloadDefaults(requested)
                }
            },
        ) {
            summary =
                downloader.downloadDefaults(store, requested) { progress ->
                    downloadProgress = progress
                }
        }
    }

    fun refreshAll() {
        var summary = FilterRefreshResult(0, 0, emptyList())
        val pending = lists.filter { it.sourceUrl != null }
        update(
            progressMessage =
                context.getString(R.string.ui_downloading_filter_lists),
            initialProgress =
                pending.firstOrNull()?.let {
                    startingProgress(it.displayName(), pending.size)
                },
            onSuccess = {
                reportBatch(summary, FilterListBatchAction.REFRESH) { refreshAll() }
            },
        ) {
            summary =
                downloader.refreshAll(store) { progress ->
                    downloadProgress = progress
                }
        }
    }

    fun refreshSelected(ids: Set<String>) {
        var summary = FilterRefreshResult(0, 0, emptyList())
        val pending = lists.filter { it.id in ids && it.sourceUrl != null }
        update(
            affectsRules =
                lists.any {
                    it.id in ids && it.enabled && it.sourceUrl != null
                },
            progressMessage =
                context.getString(R.string.ui_downloading_selected_filter_lists),
            initialProgress =
                pending.firstOrNull()?.let {
                    startingProgress(it.displayName(), pending.size)
                },
            onSuccess = {
                reportBatch(summary, FilterListBatchAction.REFRESH) {
                    refreshSelected(ids)
                }
            },
        ) {
            summary =
                downloader.refreshSelected(store, ids) { progress ->
                    downloadProgress = progress
                }
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
            .getOrNull() ?: context.getString(R.string.ui_imported_filter_list)

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
                        editError =
                            context.getString(
                                R.string.ui_couldn_t_open_this_list_please_try_again
                            )
                    }
                } finally {
                    if (pendingEdit?.id == item.id) {
                        editFileLoaded = true
                    }
                }
            }
        }
    }

    val selectAllDescription = stringResource(R.string.ui_select_all_filter_lists)
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
                            stringResource(
                                R.string
                                    .ui_content_blocking_is_disabled_the_settings_below_will_n
                            ),
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
                                    stringResource(R.string.ui_actions),
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
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.ui_enable_selected
                                                )
                                            )
                                        },
                                        enabled =
                                            !busy &&
                                                selectedLists.any { !it.enabled },
                                        onClick = {
                                            bulkActionsExpanded = false
                                            val ids =
                                                selectedLists.map { it.id }.toSet()
                                            update(
                                                successMessage =
                                                    context.getString(
                                                        R.string
                                                            .ui_selected_lists_enabled
                                                    )
                                            ) {
                                                store.setEnabled(ids, true)
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.ui_disable_selected
                                                )
                                            )
                                        },
                                        enabled =
                                            !busy &&
                                                selectedLists.any { it.enabled },
                                        onClick = {
                                            bulkActionsExpanded = false
                                            val ids =
                                                selectedLists.map { it.id }.toSet()
                                            update(
                                                successMessage =
                                                    context.getString(
                                                        R.string
                                                            .ui_selected_lists_disabled
                                                    )
                                            ) {
                                                store.setEnabled(ids, false)
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.ui_update_selected
                                                )
                                            )
                                        },
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
                                                stringResource(
                                                    R.string.ui_remove_selected
                                                ),
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
                                        text = {
                                            Text(stringResource(R.string.ui_manage))
                                        },
                                        enabled = !busy && lists.isNotEmpty(),
                                        onClick = {
                                            bulkActionsExpanded = false
                                            selectedIds = emptySet()
                                            managing = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.ui_enable_all
                                                )
                                            )
                                        },
                                        enabled = !busy && lists.any { !it.enabled },
                                        onClick = {
                                            bulkActionsExpanded = false
                                            update(
                                                successMessage =
                                                    context.getString(
                                                        R.string.ui_all_lists_enabled
                                                    )
                                            ) {
                                                store.setAllEnabled(true)
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.ui_disable_all
                                                )
                                            )
                                        },
                                        enabled = !busy && lists.any { it.enabled },
                                        onClick = {
                                            bulkActionsExpanded = false
                                            update(
                                                successMessage =
                                                    context.getString(
                                                        R.string
                                                            .ui_all_lists_disabled
                                                    )
                                            ) {
                                                store.setAllEnabled(false)
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.ui_refresh_all
                                                )
                                            )
                                        },
                                        enabled =
                                            !busy &&
                                                lists.any { it.sourceUrl != null },
                                        onClick = {
                                            bulkActionsExpanded = false
                                            refreshAll()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.ui_add_default
                                                )
                                            )
                                        },
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
                                Text(stringResource(R.string.ui_done))
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
                                    stringResource(R.string.ui_add_list),
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
                                stringResource(
                                    R.string.ui_blocking_couldn_t_be_updated
                                ),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { blocker.reload() },
                                enabled = !busy,
                            ) {
                                Text(stringResource(R.string.ui_retry))
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
                            stringResource(R.string.ui_filter_lists),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                R.string.ui_lists_enabled_count,
                                lists.count { it.enabled }.toString(),
                                lists.size.toString(),
                            ),
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
                        val selectionEnabled = !busy && lists.isNotEmpty()
                        val toggleAllSelection = {
                            selectedIds =
                                if (selectedLists.size == lists.size) {
                                    emptySet()
                                } else {
                                    lists.map { it.id }.toSet()
                                }
                        }
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
                                    enabled = selectionEnabled,
                                    onClick = toggleAllSelection,
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription = selectAllDescription
                                        },
                                )
                                Text(
                                    stringResource(
                                        R.string.ui_value_selected,
                                        selectedLists.size,
                                    ),
                                    modifier =
                                        Modifier.clickable(
                                            enabled = selectionEnabled,
                                            indication = null,
                                            interactionSource =
                                                remember {
                                                    MutableInteractionSource()
                                                },
                                            onClick = toggleAllSelection,
                                        ),
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
                                                successMessage =
                                                    context.getString(
                                                        R.string.ui_list_refreshed
                                                    ),
                                                progressMessage =
                                                    context.getString(
                                                        R.string
                                                            .ui_downloading_filter_list
                                                    ),
                                                initialProgress =
                                                    startingProgress(
                                                        item.displayName()
                                                    ),
                                            ) {
                                                downloader.refresh(
                                                    item,
                                                    store,
                                                    singleDownloadProgress(
                                                        item.displayName()
                                                    ),
                                                )
                                            }
                                        },
                                        onEdit = { openEditor(item) },
                                        onInfo = { pendingInfo = item },
                                        onClone = {
                                            update(
                                                affectsRules = false,
                                                successMessage =
                                                    context.getString(
                                                        R.string
                                                            .ui_list_cloned_disabled
                                                    ),
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
                                                        stringResource(
                                                            R.string
                                                                .ui_reorder_value,
                                                            item.displayName(),
                                                        ),
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
                FilterListProgressOverlay(
                    message = workingMessage.orEmpty(),
                    progress = downloadProgress,
                    modifier = Modifier.matchParentSize(),
                )
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
            title = { Text(stringResource(R.string.ui_add_default_lists)) },
            text = {
                Column(
                    modifier =
                        Modifier.heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.ui_the_following_lists_will_be_added)
                    )
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
                    Text(stringResource(R.string.ui_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDefaults = emptyList() }) {
                    Text(stringResource(R.string.ui_cancel))
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
                        stringResource(R.string.ui_remove_filter_list)
                    } else {
                        stringResource(
                            R.string.ui_remove_value_filter_lists,
                            removing.size,
                        )
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
                    Text(
                        stringResource(R.string.ui_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRemoval = emptyList() },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.ui_cancel))
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
                    context.getString(
                        R.string.ui_a_url_list_with_this_address_already_exists
                    )
                } else {
                    editError
                },
            busy = busy,
            canSave = canSave,
            progressMessage = workingMessage.orEmpty(),
            downloadProgress = downloadProgress,
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
                    successMessage = context.getString(R.string.ui_list_saved),
                    onFailure = { editError = it },
                    progressMessage =
                        context.getString(
                            if (item.type == FilterListType.URL && changedUrl) {
                                R.string.ui_downloading_filter_list
                            } else {
                                R.string.ui_validating_and_saving_the_filter_list
                            }
                        ),
                    initialProgress =
                        if (item.type == FilterListType.URL && changedUrl) {
                            startingProgress(name)
                        } else {
                            null
                        },
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
                                                context.getString(
                                                    R.string
                                                        .ui_cannot_open_the_selected_file
                                                )
                                            )

                                    typedContent != null ->
                                        typedContent.byteInputStream(Charsets.UTF_8)

                                    else -> null
                                }
                            store.updateFile(item.id, name, input)
                        }

                        FilterListType.URL ->
                            downloader.editUrl(
                                item,
                                name,
                                url,
                                store,
                                singleDownloadProgress(name),
                            )

                        FilterListType.PRESET ->
                            throw IllegalArgumentException(
                                context.getString(
                                    R.string.ui_preset_lists_are_read_only
                                )
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
            downloadProgress = downloadProgress,
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
                    successMessage = context.getString(R.string.ui_list_added),
                    progressMessage =
                        context.getString(
                            R.string.ui_reading_and_validating_the_filter_list
                        ),
                    onStored = { showAddDialog = false },
                    onFailure = { addError = it },
                ) {
                    val input =
                        if (uri != null) {
                            context.contentResolver.openInputStream(uri)
                                ?: throw IOException(
                                    context.getString(
                                        R.string.ui_cannot_open_the_selected_file
                                    )
                                )
                        } else {
                            text.byteInputStream(Charsets.UTF_8)
                        }
                    store.importFrom(name, input)
                }
            },
            onDownloadUrl = { url, name ->
                update(
                    successMessage = context.getString(R.string.ui_list_added),
                    progressMessage =
                        context.getString(R.string.ui_downloading_filter_list),
                    initialProgress = startingProgress(name),
                    onStored = { showAddDialog = false },
                    onFailure = { addError = it },
                ) {
                    downloader.downloadUrl(
                        url,
                        name,
                        store,
                        singleDownloadProgress(name),
                    )
                }
            },
            onDownloadPreset = { suggestion ->
                update(
                    successMessage = context.getString(R.string.ui_list_added),
                    progressMessage =
                        context.getString(R.string.ui_downloading_filter_list),
                    initialProgress = startingProgress(suggestion.name),
                    onStored = { showAddDialog = false },
                    onFailure = { addError = it },
                ) {
                    downloader.download(
                        suggestion,
                        store,
                        singleDownloadProgress(suggestion.name),
                    )
                }
            },
        )
    }
}

/**
 * Displays progress above the existing content without changing its measured size.
 */
@Composable
private fun FilterListProgressOverlay(
    message: String,
    progress: FilterListBatchProgress?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        FilterListDownloadStatus(
            message = message,
            progress = progress,
            modifier = Modifier.padding(12.dp).widthIn(max = 400.dp),
        )
    }
}

/**
 * Shows completed-list count and actual transferred bytes, never estimated
 * percentages.
 */
@Composable
internal fun FilterListDownloadStatus(
    message: String,
    progress: FilterListBatchProgress?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val transfer = progress?.download
    val batch = progress?.takeIf { it.total > 1 }
    val stage =
        when (transfer?.stage) {
            FilterListDownloadStage.CONNECTING ->
                stringResource(R.string.ui_connecting_to_filter_list)
            FilterListDownloadStage.DOWNLOADING ->
                stringResource(R.string.ui_downloading_filter_list)
            FilterListDownloadStage.SAVING ->
                stringResource(R.string.ui_validating_and_saving_the_filter_list)
            FilterListDownloadStage.FINISHED ->
                stringResource(R.string.ui_filter_list_processed)
            null -> stringResource(R.string.ui_preparing_filter_list)
        }
    val bytes =
        if (transfer?.stage == FilterListDownloadStage.DOWNLOADING) {
            val received = Formatter.formatShortFileSize(context, transfer.bytesRead)
            transfer.totalBytes?.let { total ->
                stringResource(
                    R.string.ui_filter_list_current_file_bytes,
                    received,
                    Formatter.formatShortFileSize(context, total),
                )
            }
                ?: if (transfer.bytesRead > 0) {
                    stringResource(
                        R.string.ui_filter_list_current_file_received,
                        received,
                    )
                } else {
                    ""
                }
        } else {
            ""
        }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("filter_list_download_status"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(R.drawable.outline_cloud_download_24),
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        message,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (batch != null) {
                            stringResource(
                                R.string.ui_filter_list_progress_count,
                                batch.current,
                                batch.total,
                            )
                        } else {
                            stringResource(R.string.ui_filter_list_current_progress)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                progress?.name ?: stringResource(R.string.ui_preparing_filter_list),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (batch != null) {
                val overallDescription =
                    stringResource(R.string.ui_filter_list_overall_progress)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        overallDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${batch.completed}/${batch.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { batch.completed.toFloat() / batch.total },
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            contentDescription = overallDescription
                        },
                )
            }
            Text(
                stage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val currentDescription =
                stringResource(R.string.ui_filter_list_current_progress)
            val currentProgressModifier =
                Modifier.fillMaxWidth().semantics {
                    contentDescription = currentDescription
                }
            when (transfer?.stage) {
                FilterListDownloadStage.FINISHED ->
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = currentProgressModifier,
                    )

                FilterListDownloadStage.DOWNLOADING if transfer.totalBytes != null ->
                    LinearProgressIndicator(
                        progress = {
                            (transfer.bytesRead.toFloat() / transfer.totalBytes)
                                .coerceIn(0f, 1f)
                        },
                        modifier = currentProgressModifier,
                    )

                else -> LinearProgressIndicator(modifier = currentProgressModifier)
            }
            Text(
                bytes,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilterListBullet(name: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.ui_bullet),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                stringResource(R.string.ui_no_filter_lists_have_been_added),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAddDefault, enabled = enabled) {
                Text(stringResource(R.string.ui_add_default))
            }
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
            failure =
                context.getString(
                    R.string.ui_list_information_is_unavailable_please_try_again
                )
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
                            InfoSection(stringResource(R.string.ui_source_url)) {
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
                        InfoSection(stringResource(R.string.ui_list_details)) {
                            InfoStat(
                                stringResource(R.string.ui_type),
                                when (item.type) {
                                    FilterListType.FILE ->
                                        stringResource(R.string.ui_custom)
                                    FilterListType.URL ->
                                        stringResource(R.string.ui_url)
                                    FilterListType.PRESET ->
                                        stringResource(R.string.ui_preset)
                                },
                            )
                            InfoStat(
                                stringResource(R.string.ui_status_2),
                                if (item.enabled) {
                                    stringResource(R.string.ui_enabled)
                                } else {
                                    stringResource(R.string.ui_disabled)
                                },
                            )
                            InfoStat(
                                stringResource(R.string.ui_file_size),
                                fileSize?.let {
                                    Formatter.formatShortFileSize(context, it)
                                },
                                unavailable,
                            )
                            InfoStat(
                                stringResource(R.string.ui_last_updated),
                                when {
                                    item.type == FilterListType.FILE ->
                                        stringResource(R.string.ui_not_applicable)

                                    item.lastRefreshedAt == null ->
                                        stringResource(R.string.ui_unknown)

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
                        InfoSection(stringResource(R.string.ui_rules)) {
                            InfoStat(
                                stringResource(R.string.ui_network),
                                details?.let { numbers.format(it.networkRules) },
                                unavailable,
                            )
                            InfoStat(
                                stringResource(R.string.ui_cosmetic),
                                details?.let { numbers.format(it.cosmeticRules) },
                                unavailable,
                            )
                            InfoStat(
                                stringResource(R.string.ui_scriptlets),
                                details?.let { numbers.format(it.scriptletRules) },
                                unavailable,
                            )
                            InfoStat(
                                stringResource(R.string.ui_skipped),
                                details?.let { numbers.format(it.skippedRules) },
                                unavailable,
                            )
                            InfoStat(
                                stringResource(R.string.ui_invalid),
                                details?.let { numbers.format(it.invalidRules) },
                                unavailable,
                            )
                            InfoStat(
                                stringResource(R.string.ui_total),
                                details?.let { numbers.format(it.totalRules) },
                                unavailable,
                            )
                        }
                        details
                            ?.takeIf { it.partialRules > 0 }
                            ?.let { info ->
                                InfoSection(
                                    stringResource(
                                        R.string.ui_partially_supported_rules
                                    )
                                ) {
                                    InfoStat(
                                        $$"$all",
                                        numbers.format(info.partialRules),
                                    )
                                    Text(
                                        stringResource(
                                            R.string
                                                .ui_applies_to_intercepted_network_requests_and_page_navig_2
                                        ),
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        details
                            ?.skipReasons
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { reasons ->
                                InfoSection(
                                    stringResource(R.string.ui_skipped_rules)
                                ) {
                                    reasons.entries
                                        .sortedBy { it.key.ordinal }
                                        .forEach { (reason, count) ->
                                            InfoStat(
                                                stringResource(
                                                    reason.displayLabelRes
                                                ),
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
                                stringResource(R.string.ui_scroll_for_more),
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
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ui_close))
                    }
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
    val loadingDescription = stringResource(R.string.ui_loading_value, label)
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
                        value ?: stringResource(R.string.ui_unavailable),
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
                                contentDescription = loadingDescription
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
    val selectDescription = stringResource(R.string.ui_select_value, name)
    val filterDescription = stringResource(R.string.ui_filter_value, name)
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
                        Modifier.semantics {
                            contentDescription = selectDescription
                        },
                )
            } else {
                Switch(
                    checked = item.enabled,
                    enabled = !busy,
                    onCheckedChange = onEnabledChange,
                    modifier =
                        Modifier.semantics {
                            contentDescription = filterDescription
                        },
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
                            FilterListType.FILE -> stringResource(R.string.ui_custom)
                            FilterListType.URL -> stringResource(R.string.ui_url)
                            FilterListType.PRESET ->
                                stringResource(R.string.ui_preset)
                        }
                    val status =
                        if (item.enabled) {
                            stringResource(R.string.ui_enabled)
                        } else {
                            stringResource(R.string.ui_disabled)
                        }
                    Text(
                        if (managing) {
                            stringResource(R.string.ui_value_value_3, type, status)
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
                            contentDescription =
                                stringResource(R.string.ui_options_for_value, name),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (item.type != FilterListType.PRESET) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_edit)) },
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
                                text = { Text(stringResource(R.string.ui_refresh)) },
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
                            text = { Text(stringResource(R.string.ui_info)) },
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
                                text = { Text(stringResource(R.string.ui_clone)) },
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
                            text = { Text(stringResource(R.string.ui_delete)) },
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
            FilterListType.FILE -> stringResource(R.string.ui_custom)
            FilterListType.URL -> stringResource(R.string.ui_url)
            FilterListType.PRESET -> stringResource(R.string.ui_preset)
        }
    val listTypeDescription = stringResource(R.string.ui_list_type)
    Box(
        modifier = Modifier.fillMaxWidth().onSizeChanged { anchorWidth = it.width }
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier.fillMaxWidth().semantics {
                    contentDescription = listTypeDescription
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
                                    FilterListType.FILE ->
                                        stringResource(R.string.ui_custom)
                                    FilterListType.URL ->
                                        stringResource(R.string.ui_url)
                                    FilterListType.PRESET ->
                                        stringResource(R.string.ui_preset)
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
    downloadProgress: FilterListBatchProgress?,
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
    val selectedFileLabel = stringResource(R.string.ui_selected_file)
    val tooLongErrorLabel =
        stringResource(
            R.string.ui_text_is_too_long_to_edit_here_128_kib_max_import_a_fil
        )
    val nameDescription = stringResource(R.string.ui_name)
    val urlDescription = stringResource(R.string.ui_url)
    val presetDescription = stringResource(R.string.ui_preset)
    var selectedFileName by remember { mutableStateOf(selectedFileLabel) }
    var text by rememberSaveable { mutableStateOf("") }
    var textError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPresetUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var presetsExpanded by remember { mutableStateOf(false) }
    var presetDropdownWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val availablePresets = SuggestedFilterLists.availablePresets(lists)
    val suggestion =
        availablePresets.firstOrNull { it.url == selectedPresetUrl }
            ?: availablePresets.firstOrNull()
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
        selectedFileName = selectedFileLabel
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
                    .getOrNull() ?: selectedFileLabel
            }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_add_filter_list)) },
        text = {
            Box(
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 460.dp)
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterListField(stringResource(R.string.ui_list_type)) {
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
                        FilterListField(stringResource(R.string.ui_name)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    onClearError()
                                },
                                modifier =
                                    Modifier.fillMaxWidth().semantics {
                                        contentDescription = nameDescription
                                    },
                                placeholder = {
                                    Text(stringResource(R.string.ui_e_g_my_filters))
                                },
                                singleLine = true,
                                enabled = !busy,
                                isError = name.isNotEmpty() && !validListName(name),
                            )
                        }
                    }
                    when (type) {
                        FilterListType.FILE -> {
                            if (selectedFile == null) {
                                FilterListField(
                                    stringResource(R.string.ui_filter_rules)
                                ) {
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
                                                textError = tooLongErrorLabel
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = {
                                            Text(
                                                stringResource(
                                                    R.string
                                                        .ui_type_or_paste_filter_rules
                                                )
                                            )
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
                                            stringResource(
                                                R.string.ui_selected_file
                                            ),
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
                                            stringResource(R.string.ui_import_file)
                                        } else {
                                            stringResource(R.string.ui_change_file)
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
                                            stringResource(R.string.ui_use_text),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        FilterListType.URL -> {
                            FilterListField(stringResource(R.string.ui_url)) {
                                OutlinedTextField(
                                    value = url,
                                    onValueChange = {
                                        url = it
                                        onClearError()
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth().semantics {
                                            contentDescription = urlDescription
                                        },
                                    singleLine = true,
                                    enabled = !busy,
                                    isError = duplicateUrl,
                                    supportingText =
                                        if (duplicateUrl) {
                                            ({
                                                Text(
                                                    stringResource(
                                                        R.string
                                                            .ui_this_address_is_already_in_a_url_list
                                                    )
                                                )
                                            })
                                        } else {
                                            null
                                        },
                                )
                            }
                        }

                        FilterListType.PRESET -> {
                            FilterListField(stringResource(R.string.ui_preset)) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth().onSizeChanged {
                                            presetDropdownWidth = it.width
                                        }
                                ) {
                                    OutlinedButton(
                                        onClick = { presetsExpanded = true },
                                        enabled =
                                            !busy && availablePresets.isNotEmpty(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier =
                                            Modifier.fillMaxWidth().semantics {
                                                contentDescription =
                                                    presetDescription
                                            },
                                    ) {
                                        Text(
                                            suggestion?.name
                                                ?: stringResource(
                                                    R.string
                                                        .ui_all_preset_lists_added
                                                ),
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
                                        availablePresets.forEach { preset ->
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
                                                    selectedPresetUrl = preset.url
                                                    presetsExpanded = false
                                                    onClearError()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            if (suggestion != null) {
                                Surface(
                                    color =
                                        MaterialTheme.colorScheme
                                            .surfaceContainerLow,
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
                                                stringResource(R.string.ui_category),
                                                style =
                                                    MaterialTheme.typography
                                                        .labelSmall,
                                                color =
                                                    MaterialTheme.colorScheme
                                                        .onSurfaceVariant,
                                            )
                                            Text(
                                                suggestion.description,
                                                style =
                                                    MaterialTheme.typography
                                                        .bodyMedium,
                                            )
                                        }
                                        Column(
                                            verticalArrangement =
                                                Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                stringResource(
                                                    R.string.ui_source_url
                                                ),
                                                style =
                                                    MaterialTheme.typography
                                                        .labelSmall,
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
                                                    modifier =
                                                        Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                if (busy && progressMessage.isNotBlank()) {
                    FilterListProgressOverlay(
                        message = progressMessage,
                        progress = downloadProgress,
                        modifier = Modifier.matchParentSize(),
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

                        FilterListType.PRESET -> suggestion?.let(onDownloadPreset)
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

                            FilterListType.PRESET -> suggestion != null
                        },
            ) {
                Text(
                    if (type == FilterListType.FILE) {
                        stringResource(R.string.ui_add)
                    } else {
                        stringResource(R.string.ui_download)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.ui_cancel))
            }
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
    progressMessage: String,
    downloadProgress: FilterListBatchProgress?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_edit_filter_list)) },
        text = {
            Box(
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 380.dp)
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(R.string.ui_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy,
                        isError = !validListName(name),
                    )
                    if (item.type != FilterListType.FILE) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = onUrlChange,
                            label = { Text(stringResource(R.string.ui_url)) },
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
                                label = {
                                    Text(stringResource(R.string.ui_filter_rules))
                                },
                                modifier =
                                    Modifier.fillMaxWidth().heightIn(min = 130.dp),
                                minLines = 5,
                                maxLines = 10,
                                enabled = !busy && replacementName == null,
                            )
                        } else {
                            Text(
                                stringResource(
                                    R.string
                                        .ui_this_list_is_too_large_to_edit_inline_choose_a_replace
                                ),
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
                                    stringResource(R.string.ui_replace_from_file)
                                } else {
                                    stringResource(
                                        R.string.ui_change_replacement_file
                                    )
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
                                    Text(stringResource(R.string.ui_clear))
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                if (busy && progressMessage.isNotBlank()) {
                    FilterListProgressOverlay(
                        message = progressMessage,
                        progress = downloadProgress,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) {
                Text(stringResource(R.string.ui_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.ui_cancel))
            }
        },
    )
}
