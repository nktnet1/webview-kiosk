package uk.nktnet.webviewkiosk.ui.components.webview

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.states.UserInteractionModifier
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation
import java.util.Date
import java.util.concurrent.TimeUnit

private fun formatDatetime(context: Context, timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val timeAgo = when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} min ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} h ago"
        else -> "${TimeUnit.MILLISECONDS.toDays(diff)} d ago"
    }

    val dateTime = DateFormat.getMediumDateFormat(context).format(Date(timestamp)) +
            " " +
            DateFormat.getTimeFormat(context).format(Date(timestamp))

    return "$timeAgo • $dateTime"
}

@Composable
fun HistoryDialog(
    customLoadUrl: (newUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val systemSettings = remember { SystemSettings(context) }
    var history by remember { mutableStateOf(systemSettings.historyStack) }
    var isUpdating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun displayUrl(url: String): String {
        val prefix = "file://"
        val dir = Constants.WEB_CONTENT_FILES_DIR
        return if (url.startsWith(prefix) && url.contains( Constants.WEB_CONTENT_FILES_DIR)) {
            val start = url.indexOf(prefix) + prefix.length
            val end = url.indexOf(dir) + dir.length
            url.substring(0, start) + "..." + url.substring(end)
        } else url
    }

    LaunchedEffect(Unit) {
        val currentIndex = systemSettings.historyIndex.coerceIn(0, history.lastIndex)
        listState.scrollToItem(currentIndex)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = UserInteractionModifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("History", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState
                ) {
                    items(history, key = { it.id }) { item ->
                        val index = history.indexOf(item)
                        val isCurrent = index == systemSettings.historyIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = !isUpdating && !isCurrent,
                                        onClick = {
                                            isUpdating = true
                                            WebViewNavigation.navigateToIndex(customLoadUrl, systemSettings, index)
                                            isUpdating = false
                                            onDismiss()
                                        }
                                    )
                                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("[$index] ")
                                            }
                                            append(displayUrl(item.url).toCharArray().joinToString("\u200B"))
                                        },
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        style = if (isCurrent)
                                            MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        else
                                            MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = formatDatetime(context, item.visitedAt),
                                        fontStyle = FontStyle.Italic,
                                        modifier = Modifier.padding(top = 2.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    enabled = !isUpdating && !isCurrent,
                                    onClick = {
                                        isUpdating = true
                                        WebViewNavigation.removeHistoryAtIndex(systemSettings, index)
                                        history = systemSettings.historyStack
                                        isUpdating = false
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.baseline_clear_24),
                                        contentDescription = "Delete",
                                        tint = if (!isUpdating && !isCurrent)
                                            MaterialTheme.colorScheme.error
                                        else
                                            LocalContentColor.current
                                    )
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
