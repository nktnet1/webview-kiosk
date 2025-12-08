package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.ui.components.apps.AppList
import uk.nktnet.webviewkiosk.ui.components.apps.AppSearchBar
import uk.nktnet.webviewkiosk.utils.openPackage
import uk.nktnet.webviewkiosk.config.data.AppInfo

@Composable
fun AppLauncherDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (!showDialog) {
        return
    }

    val context = LocalContext.current

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var ascending by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = DeviceOwnerManager.getLaunchableApps(context)
        loading = false
    }

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
                Text("Launch Apps", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                AppSearchBar(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = TextFieldValue(it) },
                    onSortToggle = { ascending = !ascending },
                    appCount = apps.size,
                    ascending = ascending,
                )

                Spacer(Modifier.height(8.dp))

                if (loading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredApps.isEmpty()) {
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
                        onSelectApp = { openPackage(context, it.packageName) },
                        modifier = Modifier.weight(1f)
                    )
                }

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
