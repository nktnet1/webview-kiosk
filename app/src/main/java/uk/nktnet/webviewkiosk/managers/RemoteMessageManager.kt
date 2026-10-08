package uk.nktnet.webviewkiosk.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.handlers.RemoteInboundHandler
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

object RemoteMessageManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commandsMutex = Mutex()
    private val settingsMutex = Mutex()
    private val settingsAppliedMutex = Mutex()
    private val requestsMutex = Mutex()

    private data class MqttCommandHost(val context: Context, val canHandle: () -> Boolean)
    private data class WebViewCommandHandler(
        val canHandle: () -> Boolean,
        val handle: (InboundCommandMessage) -> Unit,
    )

    private val mqttCommandHosts = CopyOnWriteArrayList<MqttCommandHost>()
    private val webViewCommandHandlers = CopyOnWriteArrayList<WebViewCommandHandler>()

    data class RemoteMessage<T>(
        val message: T,
        val source: Source
    ) {
        enum class Source { MQTT, UNIFIEDPUSH }

        private val claimed = AtomicBoolean(false)

        // Activity/service collectors share this claim for settings and requests.
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

    fun registerMqttCommandHost(context: Context, canHandle: () -> Boolean): () -> Unit {
        val host = MqttCommandHost(context.applicationContext, canHandle)
        mqttCommandHosts.add(host)
        return { mqttCommandHosts.remove(host) }
    }

    fun registerWebViewCommandHandler(
        canHandle: () -> Boolean,
        handle: (InboundCommandMessage) -> Unit,
    ): () -> Unit {
        val handler = WebViewCommandHandler(canHandle, handle)
        webViewCommandHandlers.add(handler)
        return { webViewCommandHandlers.remove(handler) }
    }

    fun emitCommand(
        command: InboundCommandMessage,
        source: RemoteMessage.Source,
        context: Context? = null,
        onFailure: ((Exception) -> Unit)? = null,
    ) {
        val appContext = context?.applicationContext
        // Queue before dispatching, and hold the FIFO slot through native and UI execution.
        // The process scope keeps pending commands alive if a connector callback ends.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            commandsMutex.withLock {
                withContext(Dispatchers.Main) {
                    val nativeContext = appContext ?: mqttCommandHosts
                        .firstOrNull { source == RemoteMessage.Source.MQTT && it.canHandle() }
                        ?.context

                    fun reportFailure(e: Exception) {
                        Log.e(javaClass.simpleName, "Failed to handle $source command", e)
                        if (onFailure != null) {
                            onFailure(e)
                        } else if (nativeContext != null) {
                            ToastManager.show(nativeContext, "$source: failed to handle command.")
                        }
                    }

                    try {
                        if (nativeContext != null) {
                            withContext(Dispatchers.IO) {
                                RemoteInboundHandler.handleInboundCommand(nativeContext, command)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportFailure(e)
                    }
                    try {
                        // Resolve the active screen after native work, since it may have changed
                        // while device-owner initialization was suspended.
                        webViewCommandHandlers.firstOrNull { it.canHandle() }?.handle(command)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportFailure(e)
                    }
                    // This flow is an observation of completed dispatch, not an execution queue.
                    commandsFlow.emit(RemoteMessage(command, source))
                }
            }
        }
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
