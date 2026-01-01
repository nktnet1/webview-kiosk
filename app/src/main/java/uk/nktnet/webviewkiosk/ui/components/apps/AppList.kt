package uk.nktnet.webviewkiosk.ui.components.apps

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uk.nktnet.webviewkiosk.config.data.AppInfo

@Composable
fun <T : AppInfo> AppList(
    apps: List<T>,
    modifier: Modifier = Modifier,
    onSelectApp: (T) -> Unit,
    getKey: ((T) -> String)? = null,
    getDescription: ((T) -> String)? = null,
    listState: LazyListState = rememberLazyListState()
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
    ) {
        items(
            apps,
            key = { app -> getKey?.invoke(app) ?: app.packageName }
        ) { app ->
            AppCard(
                app = app,
                description = getDescription?.invoke(app),
                onClick = { onSelectApp(app) }
            )
        }
    }
}
