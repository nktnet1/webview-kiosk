package uk.nktnet.webviewkiosk.ui.components.apps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import uk.nktnet.webviewkiosk.config.data.AppInfo

@Composable
fun <T : AppInfo> AppList(
    apps: List<T>,
    modifier: Modifier = Modifier,
    onSelectApp: (T) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = rememberLazyListState(),
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppCard(app = app, onClick = { onSelectApp(app) })
        }
    }
}
