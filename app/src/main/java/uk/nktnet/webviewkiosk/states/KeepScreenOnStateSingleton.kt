package uk.nktnet.webviewkiosk.states

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

object KeepScreenOnStateSingleton {
    val isKeepScreenOn: MutableState<Boolean> = mutableStateOf(false)

    fun setKeepScreenOn(enabled: Boolean) {
        isKeepScreenOn.value = enabled
    }
}
