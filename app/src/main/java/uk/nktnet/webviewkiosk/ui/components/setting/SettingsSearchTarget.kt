package uk.nktnet.webviewkiosk.ui.components.setting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import kotlinx.coroutines.delay

internal const val SETTINGS_SEARCH_FLASH_DELAY_MS = 1_000L
private const val SETTINGS_SEARCH_FLASH_ALPHA = 0.10f

/** A one-shot request consumed once the destination has scrolled and flashed. */
data class SettingsSearchTarget(val label: String, val onConsumed: () -> Unit)

val LocalSettingsSearchTarget = compositionLocalOf<SettingsSearchTarget?> { null }

/**
 * Scroll to the actual rendered row, then briefly flash it and clear the request.
 */
@Composable
fun Modifier.bringSearchSettingIntoView(label: String): Modifier {
    val target = LocalSettingsSearchTarget.current
    val isTarget = target?.label == label
    val requester = remember { BringIntoViewRequester() }
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(isTarget) {
        if (isTarget) {
            // The destination may have just entered composition; wait for layout.
            withFrameNanos {}
            requester.bringIntoView()
            // Let navigation settle first, then pulse neutral contrast instead of
            // using the blue primary selection colour.
            delay(SETTINGS_SEARCH_FLASH_DELAY_MS)
            flashAlpha.snapTo(SETTINGS_SEARCH_FLASH_ALPHA)
            delay(100)
            flashAlpha.animateTo(0f, tween(durationMillis = 750))
            target.onConsumed()
        }
    }

    return this.bringIntoViewRequester(requester)
        .background(
            MaterialTheme.colorScheme.onSurface.copy(alpha = flashAlpha.value)
        )
}
