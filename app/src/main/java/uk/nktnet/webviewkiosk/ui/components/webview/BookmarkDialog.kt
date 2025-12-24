package com.nktnet.webview_kiosk.ui.components.webview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.utils.handleUserKeyEvent
import com.nktnet.webview_kiosk.utils.handleUserTouchEvent

@Composable
fun BookmarksDialog(
    showBookmarkDialog: Boolean,
    onDismiss: () -> Unit,
    customLoadUrl: (newUrl: String) -> Unit,
) {
    if (!showBookmarkDialog) {
        return
    }

    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val bookmarks = remember { userSettings.websiteBookmarks.lines().filter { it.isNotBlank() } }
    val listState = rememberLazyListState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .handleUserTouchEvent()
                .handleUserKeyEvent(context, showBookmarkDialog)
                .fillMaxSize()
                .padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text("Bookmarks", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                if (bookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No bookmarks saved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState
                    ) {
                        itemsIndexed(bookmarks) { index, url ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        customLoadUrl(url)
                                        onDismiss()
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("[$index] ")
                                            }
                                            append(url.toCharArray().joinToString("\u200B"))
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
