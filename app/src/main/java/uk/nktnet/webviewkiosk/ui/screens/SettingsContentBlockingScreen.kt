package uk.nktnet.webviewkiosk.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.blocking.BlockingStatus
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
import uk.nktnet.webviewkiosk.blocking.FilterListStore
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterListDownloader
import uk.nktnet.webviewkiosk.blocking.SuggestedFilterLists
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import java.io.IOException

@Composable
fun SettingsContentBlockingScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember(context) { FilterListStore(context) }
    val blocker = remember(context) { ContentBlockingService.get(context) }
    val downloader = remember { SuggestedFilterListDownloader() }
    val scope = rememberCoroutineScope()
    val blockingStatus by blocker.status.collectAsState()
    val nativeError by blocker.lastError.collectAsState()
    var lists by remember { mutableStateOf(store.list()) }
    var busy by remember { mutableStateOf(false) }
    var downloadingUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun update(downloadUrl: String? = null, block: () -> Unit) {
        if (busy) return
        busy = true
        downloadingUrl = downloadUrl
        error = null
        scope.launch {
            try {
                lists = withContext(Dispatchers.IO) {
                    block()
                    // Ensure the engine reload is queued even if navigation cancels the UI.
                    blocker.reload()
                    store.list()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Unable to update filter lists"
            } finally {
                busy = false
                downloadingUrl = null
            }
        }
    }

    fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
    }.getOrNull() ?: "Imported filter list"

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            update {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open the selected file")
                store.importFrom(displayName(uri), stream)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp, start = 16.dp, end = 16.dp),
    ) {
        SettingLabel(navController, stringResource(R.string.settings_content_blocking_title))
        SettingDivider()
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Block matching network requests using lists you choose. " +
                    "No lists are bundled or downloaded automatically. " +
                    "Cosmetic filtering is not yet supported.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = {
                    AuthenticationManager.bypassAuthForWindow(60_000)
                    picker.launch(arrayOf("*/*"))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import filter list from file") }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when (blockingStatus) {
                BlockingStatus.ACTIVE -> Text("Network blocking active")
                BlockingStatus.LOADING -> Text("Compiling filter lists…")
                BlockingStatus.ERROR -> Text(
                    "Blocking unavailable: ${nativeError.orEmpty()}",
                    color = MaterialTheme.colorScheme.error,
                )
                BlockingStatus.DISABLED -> Unit
            }

            BlockingSectionHeading("Suggested downloads")
            Text(
                "Optional one-time downloads from independent list maintainers. " +
                    "Updates are not automatic; only download the lists you want.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SuggestedFilterLists.all.forEach { suggestion ->
                val installed = lists.any { it.sourceUrl == suggestion.url }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(suggestion.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            suggestion.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            update(downloadUrl = suggestion.url) {
                                downloader.download(suggestion, store)
                            }
                        },
                        enabled = !busy && !installed,
                    ) {
                        if (downloadingUrl == suggestion.url) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (installed) "Added" else "Download")
                        }
                    }
                }
            }

            BlockingSectionHeading("Your filter lists")
            if (lists.isEmpty()) {
                Text("No filter lists added. Network blocking is disabled.")
            }
            lists.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (item.sourceUrl != null) "Downloaded · ${if (item.enabled) "Enabled" else "Disabled"}"
                            else if (item.enabled) "Imported · Enabled" else "Imported · Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = item.enabled,
                        enabled = !busy,
                        onCheckedChange = { enabled -> update { store.setEnabled(item.id, enabled) } },
                    )
                    TextButton(
                        onClick = { update { store.remove(item.id) } },
                        enabled = !busy,
                    ) { Text("Remove") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Matches the section headings used by MQTT topic sub-settings. */
@Composable
private fun BlockingSectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
}
