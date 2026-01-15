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

    data class RemoteMessage<T>(
        val message: T,
        val source: Source
    ) {
        enum class Source { MQTT, UNIFIEDPUSH }
    }

    private val _commands = MutableSharedFlow<RemoteMessage<InboundCommandMessage>>(extraBufferCapacity = 100)
    val commands: SharedFlow<RemoteMessage<InboundCommandMessage>> get() = _commands

    private val _settings = MutableSharedFlow<RemoteMessage<InboundSettingsMessage>>(extraBufferCapacity = 100)
    val settings: SharedFlow<RemoteMessage<InboundSettingsMessage>> get() = _settings

    private val _requests = MutableSharedFlow<RemoteMessage<InboundRequestMessage>>(extraBufferCapacity = 100)
    val requests: SharedFlow<RemoteMessage<InboundRequestMessage>> get() = _requests

    fun emitCommand(command: InboundCommandMessage, source: RemoteMessage.Source) {
        scope.launch { _commands.emit(RemoteMessage(command, source)) }
    }

    fun emitSettings(settings: InboundSettingsMessage, source: RemoteMessage.Source) {
        scope.launch { _settings.emit(RemoteMessage(settings, source)) }
    }

    fun emitRequest(request: InboundRequestMessage, source: RemoteMessage.Source) {
        scope.launch { _requests.emit(RemoteMessage(request, source)) }
    }
}
