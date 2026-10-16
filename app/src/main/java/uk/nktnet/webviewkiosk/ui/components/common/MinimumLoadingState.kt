package uk.nktnet.webviewkiosk.ui.components.common

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** UI-thread presentation state; rule loading itself never waits on this timer. */
internal class MinimumLoadingState(
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val pause: suspend (Long) -> Unit = { delay(it.milliseconds) },
) {
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var shownAt = 0L
    private var generation = 0L

    fun show(message: String, restartMinimum: Boolean = false) {
        if (_message.value == null || restartMinimum) {
            shownAt = now()
        }
        generation++
        _message.value = message
    }

    suspend fun hide() {
        if (_message.value == null) {
            return
        }
        val revision = generation
        val remaining = 1_000L - (now() - shownAt)
        if (remaining > 0) {
            pause(remaining)
        }
        // A new phase or operation may have taken over while this hide was
        // suspended.
        if (revision == generation) {
            _message.value = null
        }
    }

    suspend fun awaitHidden() {
        message.first { it == null }
    }
}
