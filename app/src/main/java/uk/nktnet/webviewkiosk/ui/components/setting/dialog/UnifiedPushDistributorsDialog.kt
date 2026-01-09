package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.data.AppInfo
import uk.nktnet.webviewkiosk.managers.AppFlowManager

@Composable
fun UnifiedPushSelectorDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onSelectedApp: (app: AppInfo) -> Unit,
) {
    if (!showDialog) {
        return
    }

    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        AppFlowManager.getUnifiedPushAppsFlow(context).collect { state ->
            apps = apps + state.apps
            progress = state.progress
        }
    }

    BaseAppListDialog(
        onDismiss = onDismiss,
        title = "UnifiedPush Distributors",
        apps = apps,
        progress = progress,
        onSelectApp = onSelectedApp,
        appFilter = { app, query ->
            app.name.contains(query, ignoreCase = true)
            || app.packageName.contains(query, ignoreCase = true)
        }
    )
}
