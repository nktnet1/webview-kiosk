package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton

fun Modifier.handleUserTouchEvent(): Modifier {
    return this.pointerInteropFilter { motionEvent ->
        UserInteractionStateSingleton.onUserInteraction()
        false
    }
}

@Composable
fun Modifier.handleUserKeyEvent(context: Context, isVisible: Boolean): Modifier {
    val activity = context as? Activity ?: return this
    val activityManager = activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager
    val userSettings = UserSettings(activity)
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
            handlePreviewKeyUnlockEvent(
                activity,
                activityManager,
                userSettings,
                event.nativeKeyEvent
            )
        }
}
