package com.nktnet.webview_kiosk.ui.components.setting.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.config.data.AppInfo
import com.nktnet.webview_kiosk.ui.components.apps.AppList
import com.nktnet.webview_kiosk.ui.components.apps.AppSearchBar
import androidx.compose.ui.res.painterResource
import com.nktnet.webview_kiosk.R

@Composable
fun <T : AppInfo> BaseAppListDialog(
    onDismiss: () -> Unit,
    title: String,
    apps: List<T>,
    progress: Float,
    appFilter: (T, String) -> Boolean,
    getDescription: (T) -> String = { it.packageName },
    getKey: (T) -> String = { it.packageName },
    onSelectApp: (T) -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
    emptyContent: @Composable (() -> Unit) = { Text("No apps available.") },
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var ascending by remember { mutableStateOf(true) }

    val filteredApps by remember(searchQuery.text, apps, ascending) {
        derivedStateOf {
            apps
                .filter { app -> appFilter(app, searchQuery.text) }
                .let { filtered ->
                    if (ascending) {
                        filtered.sortedBy { it.name }
                    } else {
                        filtered.sortedByDescending { it.name }
                    }
                }
        }
    }

    LaunchedEffect(filteredApps) {
        if (filteredApps.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 12.dp),
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

                extraContent?.invoke()

                if (filteredApps.isEmpty() && progress == 1f) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 24.dp, start = 8.dp, end = 8.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        SelectionContainer {
                            emptyContent.invoke()
                        }
                    }
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
