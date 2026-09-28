package uk.nktnet.webviewkiosk.utils

import android.content.Context
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton

fun Modifier.handleUserTouchEvent(): Modifier {
    return this.pointerInteropFilter { _ ->
        UserInteractionStateSingleton.onUserInteraction()
        false
    }
}

fun Modifier.requestFocusWhenPlaced(
    focusRequester: FocusRequester,
    enabled: Boolean = true,
    requestKey: Any? = Unit,
): Modifier = composed {
    var isPlaced by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, requestKey, isPlaced) {
        if (enabled && isPlaced) {
            runCatching {
                focusRequester.requestFocus()
            }
        }
    }

    this
        .focusRequester(focusRequester)
        .onGloballyPositioned { coordinates ->
            isPlaced = coordinates.isAttached
                && coordinates.size.width > 0
                && coordinates.size.height > 0
        }
}

fun Modifier.handleUserKeyEvent(
    context: Context,
    isVisible: Boolean
): Modifier = composed {
    val focusRequester = remember { FocusRequester() }

    this
        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
        .requestFocusWhenPlaced(
            focusRequester = focusRequester,
            enabled = isVisible,
            requestKey = isVisible,
        )
        .focusable()
        .onPreviewKeyEvent { event ->
            handleKeyEvent(context, event.nativeKeyEvent)
        }
}
