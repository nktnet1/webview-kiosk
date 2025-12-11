package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.ui.components.apps.AppList
import uk.nktnet.webviewkiosk.ui.components.apps.AppSearchBar
import uk.nktnet.webviewkiosk.utils.openPackage
import uk.nktnet.webviewkiosk.config.data.LaunchableAppInfo
import uk.nktnet.webviewkiosk.managers.ToastManager

@Composable
fun AppLauncherDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (!showDialog) {
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var ascending by remember { mutableStateOf(true) }

    var apps by remember { mutableStateOf<List<LaunchableAppInfo>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    var activityDialogApp by remember { mutableStateOf<LaunchableAppInfo?>(null) }

    val filteredApps by remember(searchQuery.text, apps, ascending) {
        derivedStateOf {
            apps
                .filter {
                    it.name.contains(searchQuery.text, ignoreCase = true)
                    || it.packageName.contains(searchQuery.text, ignoreCase = true)
                }
                .sortedBy { it.name }
                .let { if (ascending) it else it.reversed() }
        }
    }

    LaunchedEffect(Unit) {
        var currentApps = emptyList<LaunchableAppInfo>()
        DeviceOwnerManager.getLaunchableAppsFlow(context)
            .collect { state ->
                currentApps = currentApps + state.apps
                apps = currentApps
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text("Apps", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                AppSearchBar(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = TextFieldValue(it) },
                    onSortToggle = { ascending = !ascending },
                    appCount = apps.size,
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
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text("No apps available.")
                    }
                } else {
                    AppList(
                        apps = filteredApps,
                        onSelectApp = { app ->
                            if (app.activities.size == 1) {
                                openPackage(
                                    context,
                                    app.packageName,
                                    app.activities.first().name
                                )
                            } else if (app.activities.size >= 2) {
                                activityDialogApp = app
                            } else {
                                ToastManager.show(context, "Error: no activities for app.")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        listState = listState,
                        getDescription = { app ->
                            if (app.activities.size > 1) {
                                "${app.packageName} (${app.activities.size})"
                            } else {
                                app.packageName
                            }
                        },
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
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.keyboard_double_arrow_up_24),
                                contentDescription = "Scroll to top"
                            )
                        }

                        IconButton(
                            enabled = listState.canScrollForward,
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        listState.layoutInfo.totalItemsCount - 1
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = R.drawable.keyboard_double_arrow_down_24
                                ),
                                contentDescription = "Scroll to bottom",
                            )
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }

    activityDialogApp?.let { app ->
        AlertDialog(
            onDismissRequest = { activityDialogApp = null },
            title = { Text(app.name) },
            text = {
                Column {
                    Text(
                        "Select an activity to launch:",
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    app.activities.forEach { activity ->
                        Button(
                            onClick = {
                                openPackage(context, app.packageName, activity.name)
                                activityDialogApp = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                activity.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

            },
            confirmButton = {
                TextButton(
                    onClick = { activityDialogApp = null }
                ) {
                    Text("Close")
                }
            }
        )
    }
}
