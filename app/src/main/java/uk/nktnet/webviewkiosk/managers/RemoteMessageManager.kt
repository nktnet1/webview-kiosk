package uk.nktnet.webviewkiosk.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import java.util.concurrent.atomic.AtomicBoolean

object RemoteMessageManager {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val commandsMutex = Mutex()
    private val settingsMutex = Mutex()
    private val settingsAppliedMutex = Mutex()
    private val requestsMutex = Mutex()

    data class RemoteMessage<T>(
        val message: T,
        val source: Source
    ) {
        enum class Source { MQTT, UNIFIEDPUSH }

        private val claimed = AtomicBoolean(false)

        // Activity/service collectors share this claim; WebView actions are handled separately.
        fun tryClaim(): Boolean = claimed.compareAndSet(false, true)
    }

    val commandsFlow: SharedFlow<RemoteMessage<InboundCommandMessage>>
        field = MutableSharedFlow<RemoteMessage<InboundCommandMessage>>(extraBufferCapacity = 100)

    val settingsFlow: SharedFlow<RemoteMessage<InboundSettingsMessage>>
        field = MutableSharedFlow<RemoteMessage<InboundSettingsMessage>>(extraBufferCapacity = 100)

    val settingsAppliedFlow: SharedFlow<RemoteMessage<InboundSettingsMessage>>
        field = MutableSharedFlow<RemoteMessage<InboundSettingsMessage>>(extraBufferCapacity = 100)

    val requestsFlow: SharedFlow<RemoteMessage<InboundRequestMessage>>
        field = MutableSharedFlow<RemoteMessage<InboundRequestMessage>>(extraBufferCapacity = 100)

    fun emitCommand(command: InboundCommandMessage, source: RemoteMessage.Source) {
        emitOrdered(commandsFlow, commandsMutex, RemoteMessage(command, source))
    }

    fun emitSettings(settings: InboundSettingsMessage, source: RemoteMessage.Source) {
        emitOrdered(settingsFlow, settingsMutex, RemoteMessage(settings, source))
    }

    fun emitSettingsApplied(settings: InboundSettingsMessage, source: RemoteMessage.Source) {
        emitOrdered(settingsAppliedFlow, settingsAppliedMutex, RemoteMessage(settings, source))
    }

    fun emitRequest(request: InboundRequestMessage, source: RemoteMessage.Source) {
        emitOrdered(requestsFlow, requestsMutex, RemoteMessage(request, source))
    }

    private fun <T> emitOrdered(flow: MutableSharedFlow<T>, mutex: Mutex, message: T) {
        // Join this stream's FIFO queue before dispatching; other streams remain independent.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                // Emit in a dispatched child so a busy subscriber cannot block the callback.
                launch { flow.emit(message) }.join()
            }
        }
    }
}
