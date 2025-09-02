package com.nktnet.webview_kiosk.ui.components

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.utils.webview.WebViewNavigation

@Composable
fun HistoryDialog(
    webView: WebView,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val systemSettings = remember { SystemSettings(context) }
    var history by remember { mutableStateOf(systemSettings.historyStack) }
    var isUpdating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val currentIndex = systemSettings.historyIndex.coerceIn(0, history.lastIndex)
        listState.scrollToItem(currentIndex)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("History", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState
                ) {
                    itemsIndexed(history) { index, item ->
                        val isCurrent = index == systemSettings.historyIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = !isUpdating && !isCurrent) {
                                    isUpdating = true
                                    WebViewNavigation.navigateToIndex(webView, systemSettings, index)
                                    isUpdating = false
                                    onDismiss()
                                },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = if (isCurrent)
                                        MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary)
                                    else
                                        MaterialTheme.typography.bodyMedium
                                )
                                IconButton(
                                    enabled = !isUpdating && !isCurrent,
                                    onClick = {
                                        isUpdating = true
                                        WebViewNavigation.removeHistoryAtIndex(systemSettings, index)
                                        history = systemSettings.historyStack
                                        isUpdating = false
                                    }
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        enabled = !isUpdating && history.any { it != history.getOrNull(systemSettings.historyIndex) },
                        onClick = {
                            isUpdating = true
                            WebViewNavigation.clearHistory(systemSettings)
                            history = systemSettings.historyStack
                            isUpdating = false
                        }
                    ) { Text("Clear All") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
