package uk.nktnet.webviewkiosk.utils

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import uk.nktnet.webviewkiosk.config.Constants

// DeadSystemRuntimeException is hidden, and DeadSystemException was added after our minimum SDK.
private val systemRestartExceptionNames = setOf(
    "android.os.DeadSystemRuntimeException",
    "android.os.DeadSystemException",
)

internal fun hasCauseNamed(failure: Throwable, names: Set<String>): Boolean {
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var cause: Throwable? = failure
    while (cause != null && visited.add(cause)) {
        if (cause.javaClass.name in names) {
            return true
        }
        cause = cause.cause
    }
    return false
}

internal fun isSystemRestartFailure(failure: Throwable): Boolean =
    hasCauseNamed(failure, systemRestartExceptionNames)

internal class SystemSafeClipboard(
    private val delegate: Clipboard,
    private val onSystemRestart: (RuntimeException) -> Unit,
    private val isSystemRestart: (Throwable) -> Boolean = ::isSystemRestartFailure,
) : Clipboard by delegate {
    override suspend fun getClipEntry(): ClipEntry? = try {
        delegate.getClipEntry()
    } catch (error: RuntimeException) {
        handleFailure(error)
        null
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        try {
            delegate.setClipEntry(clipEntry)
        } catch (error: RuntimeException) {
            handleFailure(error)
        }
    }

    private fun handleFailure(error: RuntimeException) {
        if (error is CancellationException || !isSystemRestart(error)) {
            throw error
        }
        onSystemRestart(error)
    }
}

/** Install inside each Compose root, including dialogs and embedded ComposeViews. */
@Composable
fun ProvideSystemSafeClipboard(content: @Composable () -> Unit) {
    val clipboard = LocalClipboard.current
    val guardedClipboard = remember(clipboard) {
        if (clipboard is SystemSafeClipboard) {
            clipboard
        } else {
            SystemSafeClipboard(
                delegate = clipboard,
                onSystemRestart = { error ->
                    Log.w(
                        Constants.APP_SCHEME,
                        "Clipboard unavailable during Android system restart",
                        error,
                    )
                },
            )
        }
    }
    CompositionLocalProvider(LocalClipboard provides guardedClipboard, content = content)
}
