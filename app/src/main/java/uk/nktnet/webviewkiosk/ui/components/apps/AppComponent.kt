package uk.nktnet.webviewkiosk.ui.components.apps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.managers.AppInfo

@Composable
fun <T : AppInfo> AppList(
    apps: List<T>,
    modifier: Modifier = Modifier,
    onSelectApp: (T) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppCard(app = app, onClick = { onSelectApp(app) })
        }
    }
}

