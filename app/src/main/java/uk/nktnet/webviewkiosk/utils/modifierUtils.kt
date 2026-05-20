package uk.nktnet.webviewkiosk.utils

import android.content.Context
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import kotlin.time.Duration.Companion.milliseconds

fun Modifier.handleUserTouchEvent(): Modifier {
    return this.pointerInteropFilter { _ ->
        UserInteractionStateSingleton.onUserInteraction()
        false
    }
}

fun Modifier.handleUserKeyEvent(
    context: Context,
    isVisible: Boolean
): Modifier = composed {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(100.milliseconds)
            awaitFrame()
            runCatching {
                focusRequester.requestFocus()
            }
        }
    }

    this
        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            handleKeyEvent(context, event.nativeKeyEvent)
        }
}
