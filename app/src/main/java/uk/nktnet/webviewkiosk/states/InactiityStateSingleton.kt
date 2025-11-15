package com.nktnet.webview_kiosk.states

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf

object InactivityStateSingleton {
    val lastInteractionState: MutableState<Long> = mutableLongStateOf(System.currentTimeMillis())

    fun onUserInteraction() {
        lastInteractionState.value = System.currentTimeMillis()
    }
}
