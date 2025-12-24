package com.nktnet.webview_kiosk.ui.screens

import android.content.ClipData
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.managers.MqttManager
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.managers.MqttLogEntry
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SettingsMqttDebugScreen(navController: NavController) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var ascending by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd h:mm:ss a", Locale.getDefault())

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val logs = remember {
        mutableStateListOf<MqttLogEntry>().apply {
            addAll(MqttManager.debugLogHistory.asReversed())
        }
    }

    LaunchedEffect(Unit) {
        MqttManager.debugLog.collectLatest { entry ->
            logs.add(0, entry)
        }
    }

    val filteredLogs by remember {
        derivedStateOf {
            logs
                .filter {
                    it.tag.contains(searchQuery.text, ignoreCase = true)
                    || (it.message?.contains(searchQuery.text, ignoreCase = true) == true)
                    || (it.messageId?.contains(searchQuery.text, ignoreCase = true) == true)
                }
                .sortedBy { it.timestamp }
                .let { if (ascending) it else it.reversed() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingLabel(navController = navController, label = "Debug Log")
            SettingDivider()

            Row(
                modifier = Modifier
                    .height(50.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = searchQuery.text,
                    onValueChange = { searchQuery = TextFieldValue(it) },
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(43.dp),
                    decorationBox = { innerTextField ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    MaterialTheme.shapes.small
                                )
                                .padding(start = 14.dp, end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.text.isEmpty()) {
                                    Text(
                                        text = "Search the last ${logs.size} logs",
                                        style = LocalTextStyle.current.copy(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                            fontStyle = FontStyle.Italic,
                                        )
                                    )
                                }
                                innerTextField()
                            }

                            IconButton(
                                enabled = searchQuery.text.isNotEmpty(),
                                onClick = { searchQuery = TextFieldValue("") },
                                modifier = Modifier
                                    .size(26.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_clear_24),
                                    contentDescription = "Clear",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(2.dp),
                                )
                            }
                        }
                    }
                )

                IconButton(
                    onClick = { ascending = !ascending },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .graphicsLayer(scaleX = 0.9f, scaleY = 0.9f)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            MaterialTheme.shapes.small
                        )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_sort_24),
                        contentDescription = "Sort Order",
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer(scaleY = if (ascending) -1f else 1f, scaleX = -1f)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No logs yet.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredLogs) { logEntry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        ToastManager.show(
                                            context,
                                            "Click and hold to copy message."
                                        )
                                    },
                                    onLongClick = {
                                        scope.launch {
                                            clipboard.setClipEntry(
                                                ClipData.newPlainText(
                                                    logEntry.tag,
                                                    logEntry.message
                                                ).toClipEntry()
                                            )
                                        }
                                    }
                                )
                         ) {
                            Text(
                                text = "${dateFormat.format(logEntry.timestamp)} - ${logEntry.tag}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            logEntry.messageId?.let {
                                Text(
                                    text = "messageId: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            logEntry.message?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            HorizontalDivider(
                                thickness = DividerDefaults.Thickness,
                                color = DividerDefaults.color,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }

        OutlinedButton(
            onClick = {
                MqttManager.clearLogs()
                logs.clear()
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_clear_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Clear Logs", style = MaterialTheme.typography.labelLarge)
        }
    }
}
