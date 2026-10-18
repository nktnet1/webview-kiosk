package uk.nktnet.webviewkiosk.ui.components.webview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.blocking.BlockingStatus
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService

@Composable
internal fun ContentBlockingGate(
    blocker: ContentBlockingService,
    content: @Composable () -> Unit,
) {
    val enabled by blocker.enabled.collectAsState()
    val initialLoadComplete by blocker.initialLoadComplete.collectAsState()
    val rulesPending by blocker.rulesPending.collectAsState()
    val status by blocker.status.collectAsState()
    val stage by blocker.loadStage.collectAsState()
    val waiting =
        rulesPending || !initialLoadComplete || status == BlockingStatus.LOADING
    val message =
        if (waiting) {
            stage?.description ?: "Loading content-blocking rules…"
        } else {
            null
        }
    LaunchedEffect(blocker, rulesPending) { blocker.prepareForWebView() }
    if (waiting) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        // A setting change also replaces a page that is already on screen.
        key(enabled) { content() }
    }
}
