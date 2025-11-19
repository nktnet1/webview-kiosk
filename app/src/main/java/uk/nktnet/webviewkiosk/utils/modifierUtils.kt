package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
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

fun Modifier.handleUserKeyEvent(context: Context): Modifier {
    val activity = context as? Activity ?: return this
    val activityManager = activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager
    val userSettings = UserSettings(activity)

    return this.focusable().onPreviewKeyEvent { event ->
        println("[DEBUG]: alt=${event.isAltPressed}, ctrl=${event.isCtrlPressed}, shift=${event.isShiftPressed}, meta=${event.isMetaPressed}")
        handlePreviewKeyUnlockEvent(activity, activityManager, userSettings, event.nativeKeyEvent)
    }
}
