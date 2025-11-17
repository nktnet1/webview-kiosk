package uk.nktnet.webviewkiosk.states

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter

object UserInteractionStateSingleton {
    val lastInteractionState: MutableState<Long> = mutableLongStateOf(
        System.currentTimeMillis()
    )

    fun onUserInteraction() {
        lastInteractionState.value = System.currentTimeMillis()
    }
}

val UserInteractionModifier = Modifier.pointerInteropFilter { motionEvent ->
    UserInteractionStateSingleton.onUserInteraction()
    false
}
