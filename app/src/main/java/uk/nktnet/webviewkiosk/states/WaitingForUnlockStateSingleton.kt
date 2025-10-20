package uk.nktnet.webviewkiosk.states

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object WaitingForUnlockStateSingleton {
    private val _waitingForUnlock = MutableStateFlow(false)
    val waitingForUnlock = _waitingForUnlock.asStateFlow()

    private val _unlockSuccess = MutableSharedFlow<Unit>()
    val unlockSuccess = _unlockSuccess.asSharedFlow()

    fun startWaiting() {
        _waitingForUnlock.value = true
    }

    fun stopWaiting() {
        _waitingForUnlock.value = false
    }

    suspend fun emitUnlockSuccess() {
        _unlockSuccess.emit(Unit)
    }
}
