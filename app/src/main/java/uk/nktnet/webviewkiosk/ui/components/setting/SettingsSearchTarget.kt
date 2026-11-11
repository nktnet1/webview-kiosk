package uk.nktnet.webviewkiosk.ui.components.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier

/** Only the destination of a settings-search navigation receives this label. */
val LocalSettingsSearchTarget = compositionLocalOf<String?> { null }

/**
 * Find the actual rendered row, even when preceding settings have variable heights.
 */
@Composable
fun Modifier.bringSearchSettingIntoView(label: String): Modifier {
    val isTarget = LocalSettingsSearchTarget.current == label
    val requester = remember { BringIntoViewRequester() }

    LaunchedEffect(isTarget) {
        if (isTarget) {
            // The destination may have just entered composition; wait for its first
            // layout.
            withFrameNanos {}
            requester.bringIntoView()
        }
    }

    return this.bringIntoViewRequester(requester)
        .then(
            if (isTarget) {
                Modifier.background(MaterialTheme.colorScheme.primaryContainer)
            } else {
                Modifier
            }
        )
}
