package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.data.AppInfo
import uk.nktnet.webviewkiosk.config.data.AppLoadState
import uk.nktnet.webviewkiosk.ui.components.apps.AppList
import uk.nktnet.webviewkiosk.ui.components.apps.AppSearchBar
import androidx.compose.ui.res.painterResource
import uk.nktnet.webviewkiosk.R

@Composable
fun <T : AppInfo> BaseAppListDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    title: String,
    fetchAppsFlow: suspend () -> Flow<AppLoadState<T>>,
    searchFilter: (T, String) -> Boolean = { app, query ->
        app.name.contains(query, ignoreCase = true)
        || app.packageName.contains(query, ignoreCase = true)
    },
    getDescription: (T) -> String = { it.name },
    getKey: (T) -> String = { it.packageName },
    onSelectApp: (T) -> Unit,
) {
    if (!showDialog) {
        return
    }

    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<T>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var ascending by remember { mutableStateOf(true) }

    val filteredApps by remember(searchQuery.text, apps, ascending) {
        derivedStateOf {
            apps
                .filter { app -> searchFilter(app, searchQuery.text) }
                .let { filtered ->
                    if (ascending) {
                        filtered.sortedBy { it.name }
                    } else {
                        filtered.sortedByDescending { it.name }
                    }
                }
        }
    }

    LaunchedEffect(Unit) {
        fetchAppsFlow().collect { state ->
            apps = apps + state.apps
            progress = state.progress
        }
    }

    LaunchedEffect(filteredApps) {
        if (filteredApps.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                AppSearchBar(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = TextFieldValue(it) },
                    onSortToggle = { ascending = !ascending },
                    appCount = apps.size,
                    filteredAppCount = filteredApps.size,
                    ascending = ascending,
                )

                if (progress < 1f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(4.dp)
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                }

                if (filteredApps.isEmpty() && progress == 1f) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.TopCenter
                    ) { Text("No apps available.") }
                } else {
                    AppList(
                        apps = filteredApps,
                        onSelectApp = onSelectApp,
                        getKey = getKey,
                        getDescription = getDescription,
                        listState = listState,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        IconButton(
                            enabled = listState.canScrollBackward,
                            onClick = { scope.launch { listState.animateScrollToItem(0) } }
                        ) {
                            Icon(
                                painterResource(R.drawable.keyboard_double_arrow_up_24),
                                "Scroll to top"
                            )
                        }

                        IconButton(
                            enabled = listState.canScrollForward,
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        listState.layoutInfo.totalItemsCount - 1)
                                }
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.keyboard_double_arrow_down_24),
                                "Scroll to bottom"
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
