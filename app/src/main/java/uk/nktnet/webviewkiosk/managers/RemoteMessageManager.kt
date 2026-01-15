package uk.nktnet.webviewkiosk.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage

object RemoteMessageManager {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _commands = MutableSharedFlow<InboundCommandMessage>(extraBufferCapacity = 100)
    val commands: SharedFlow<InboundCommandMessage> get() = _commands

    private val _settings = MutableSharedFlow<InboundSettingsMessage>(extraBufferCapacity = 100)
    val settings: SharedFlow<InboundSettingsMessage> get() = _settings

    private val _requests = MutableSharedFlow<InboundRequestMessage>(extraBufferCapacity = 100)
    val requests: SharedFlow<InboundRequestMessage> get() = _requests

    fun emitCommand(command: InboundCommandMessage) {
        scope.launch { _commands.emit(command) }
    }

    fun emitSettings(settings: InboundSettingsMessage) {
        scope.launch { _settings.emit(settings) }
    }

    fun emitRequest(request: InboundRequestMessage) {
        scope.launch { _requests.emit(request) }
    }
}
