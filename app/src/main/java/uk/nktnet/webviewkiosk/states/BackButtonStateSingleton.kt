package uk.nktnet.webviewkiosk.states

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object BackButtonStateSingleton {
    private val _longPressEvents = MutableSharedFlow<Unit>()
    val longPressEvents = _longPressEvents.asSharedFlow()

    suspend fun emitLongPress() {
        _longPressEvents.emit(Unit)
    }
}
