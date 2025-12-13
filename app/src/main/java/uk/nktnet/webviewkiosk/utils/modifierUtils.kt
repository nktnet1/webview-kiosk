package com.nktnet.webview_kiosk.utils

import android.content.Context
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.nktnet.webview_kiosk.states.UserInteractionStateSingleton

fun Modifier.handleUserTouchEvent(): Modifier {
    return this.pointerInteropFilter { _ ->
        UserInteractionStateSingleton.onUserInteraction()
        false
    }
}

@Composable
fun Modifier.handleUserKeyEvent(context: Context, isVisible: Boolean): Modifier {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
        }
    }

    return this
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            handleKeyEvent(context, event.nativeKeyEvent)
        }
}
