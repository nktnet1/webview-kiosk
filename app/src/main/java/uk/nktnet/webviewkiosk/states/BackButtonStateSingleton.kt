package com.nktnet.webview_kiosk.states

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object BackButtonStateSingleton {
    private val _longPress = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val longPressEvents = _longPress.asSharedFlow()

    private val _shortPress = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shortPressEvents = _shortPress.asSharedFlow()

    suspend fun emitLongPress()  {
        _longPress.emit(Unit)
        UserInteractionStateSingleton.onUserInteraction()
    }
    suspend fun emitShortPress() {
        _shortPress.emit(Unit)
        UserInteractionStateSingleton.onUserInteraction()
    }
}
