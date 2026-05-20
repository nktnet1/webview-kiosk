package uk.nktnet.webviewkiosk.states

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UserInteractionStateSingleton {
    private val _lastInteraction =
        MutableStateFlow(System.currentTimeMillis())

    val lastInteractionState: StateFlow<Long> =
        _lastInteraction.asStateFlow()

    fun onUserInteraction() {
        _lastInteraction.value = System.currentTimeMillis()
    }
}
