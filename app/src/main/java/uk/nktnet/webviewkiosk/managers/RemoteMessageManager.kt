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

    val commandsFlow: SharedFlow<RemoteMessage<InboundCommandMessage>>
        field = MutableSharedFlow<RemoteMessage<InboundCommandMessage>>(extraBufferCapacity = 100)

    val settingsFlow: SharedFlow<RemoteMessage<InboundSettingsMessage>>
        field = MutableSharedFlow<RemoteMessage<InboundSettingsMessage>>(extraBufferCapacity = 100)

    val requestsFlow: SharedFlow<RemoteMessage<InboundRequestMessage>>
        field = MutableSharedFlow<RemoteMessage<InboundRequestMessage>>(extraBufferCapacity = 100)

    fun emitCommand(command: InboundCommandMessage, source: RemoteMessage.Source) {
        scope.launch { commandsFlow.emit(RemoteMessage(command, source)) }
    }

    fun emitSettings(settings: InboundSettingsMessage, source: RemoteMessage.Source) {
        scope.launch { settingsFlow.emit(RemoteMessage(settings, source)) }
    }

    fun emitRequest(request: InboundRequestMessage, source: RemoteMessage.Source) {
        scope.launch { requestsFlow.emit(RemoteMessage(request, source)) }
    }
}
