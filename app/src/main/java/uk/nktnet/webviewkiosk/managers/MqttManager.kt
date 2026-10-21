package uk.nktnet.webviewkiosk.managers

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.MqttWebSocketConfig
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.disconnect.Mqtt5DisconnectReasonCode
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck
import java.util.Date
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.optionals.getOrNull
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.data.SystemInfo
import uk.nktnet.webviewkiosk.config.mqtt.HomeAssistantDiscovery
import uk.nktnet.webviewkiosk.config.mqtt.MqttConfig
import uk.nktnet.webviewkiosk.config.mqtt.MqttQosOption
import uk.nktnet.webviewkiosk.config.mqtt.MqttRetainHandlingOption
import uk.nktnet.webviewkiosk.config.mqtt.MqttVariableName
import uk.nktnet.webviewkiosk.config.option.LockStateType
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandJsonParser
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundErrorCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundErrorRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundLaunchablePackagesRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundLockTaskPackagesRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestJsonParser
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundStatusRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSystemInfoRequest
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundAppBackgroundEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundAppForegroundEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundApplicationRestrictionsChangedEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundConnectedEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundErrorResponse
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundEventJsonParser
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundEventMessage
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundLaunchablePackagesResponse
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundLockEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundLockTaskPackagesResponse
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundPowerPluggedEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundPowerUnpluggedEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundResponseJsonParser
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundResponseMessage
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundScreenOffEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundScreenOnEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundSettingsResponse
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundStatusResponse
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundSystemInfoResponse
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundUnlockEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundUrlChangedEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundUserPresentEvent
import uk.nktnet.webviewkiosk.utils.BaseJson
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus
import uk.nktnet.webviewkiosk.utils.filterSettingsJson
import uk.nktnet.webviewkiosk.utils.getStatus
import uk.nktnet.webviewkiosk.utils.isValidMqttPublishTopic
import uk.nktnet.webviewkiosk.utils.isValidMqttSubscribeTopic
import uk.nktnet.webviewkiosk.utils.replaceVariables

data class MqttLogEntry(
    val timestamp: Date,
    val tag: String,
    val message: String?,
    val messageId: String?,
)

object MqttManager {
    private const val DISCONNECT_EVENT_TIMEOUT_MS = 5_000L

    @Volatile private var client: Mqtt5AsyncClient? = null
    private var configurationError: String? = null
    @Volatile private lateinit var config: MqttConfig

    private val scope = CoroutineScope(Dispatchers.Default)

    private val logHistory = ArrayDeque<MqttLogEntry>(100)
    val debugLog: SharedFlow<MqttLogEntry>
        field = MutableSharedFlow<MqttLogEntry>(extraBufferCapacity = 100)
    private val pendingCancelConnect: AtomicBoolean = AtomicBoolean(false)
    // A reconnect can reuse the same HiveMQ client while replacing its broker
    // session.
    // Late callbacks from the earlier session must not be treated as current.
    private val subscriptionEpoch = AtomicLong()
    @Volatile private var homeAssistantSession: HomeAssistantMqttSession? = null

    @SuppressLint("NewApi")
    private class DisconnectOperation(val client: Mqtt5AsyncClient) {
        val completion = CompletableFuture<Void>()
        val started = AtomicBoolean(false)
    }

    @Volatile private var pendingDisconnect: DisconnectOperation? = null
    private var disconnectRequestId = 0L
    @SuppressLint("NewApi")
    private var reconnectCancellation = CompletableFuture<Void>()

    private fun addDebugLog(
        tag: String,
        message: String? = null,
        messageId: String? = null,
    ) {
        val logEntry = MqttLogEntry(Date(), tag, message, messageId)
        synchronized(logHistory) {
            if (logHistory.size >= 100) {
                logHistory.removeFirst()
            }
            logHistory.addLast(logEntry)
        }
        scope.launch {
            debugLog.emit(logEntry)
        }
    }

    val debugLogHistory: List<MqttLogEntry>
        get() = synchronized(logHistory) { logHistory.toList() }

    @Synchronized
    @SuppressLint("NewApi")
    fun updateConfig(
        context: Context,
        rebuildClient: Boolean = true,
    ) {
        val existingClient = client
        if (
            rebuildClient &&
                (pendingDisconnect != null ||
                    (existingClient != null &&
                        existingClient.state != MqttClientState.DISCONNECTED))
        ) {
            // Keep the live client's configuration until an explicit
            // disconnect/restart.
            return
        }
        val systemSettings = SystemSettings(context)
        val userSettings = UserSettings(context)

        config =
            MqttConfig(
                appInstanceId = systemSettings.appInstanceId,
                enabled = userSettings.mqttEnabled,
                clientId = userSettings.mqttClientId,
                serverHost = userSettings.mqttServerHost,
                serverPort = userSettings.mqttServerPort,
                username = userSettings.mqttUsername,
                password = userSettings.mqttPassword,
                useTls = userSettings.mqttUseTls,
                cleanStart = userSettings.mqttCleanStart,
                keepAlive = userSettings.mqttKeepAlive,
                mqttConnectTimeout = userSettings.mqttConnectTimeout,
                socketConnectTimeout = userSettings.mqttSocketConnectTimeout,
                automaticReconnect = userSettings.mqttAutomaticReconnect,
                sessionExpiryInterval = userSettings.mqttSessionExpiryInterval,
                useWebSocket = userSettings.mqttUseWebSocket,
                webSocketServerPath = userSettings.mqttWebSocketServerPath,
                publishEventTopic = userSettings.mqttPublishEventTopic,
                publishEventQos = userSettings.mqttPublishEventQos,
                publishEventRetain = userSettings.mqttPublishEventRetain,
                publishResponseTopic = userSettings.mqttPublishResponseTopic,
                publishResponseQos = userSettings.mqttPublishResponseQos,
                publishResponseRetain = userSettings.mqttPublishResponseRetain,
                subscribeCommandTopic = userSettings.mqttSubscribeCommandTopic,
                subscribeCommandQos = userSettings.mqttSubscribeCommandQos,
                subscribeCommandRetainHandling =
                    userSettings.mqttSubscribeCommandRetainHandling,
                subscribeCommandRetainAsPublished =
                    userSettings.mqttSubscribeCommandRetainAsPublished,
                subscribeSettingsTopic = userSettings.mqttSubscribeSettingsTopic,
                subscribeSettingsQos = userSettings.mqttSubscribeSettingsQos,
                subscribeSettingsRetainHandling =
                    userSettings.mqttSubscribeSettingsRetainHandling,
                subscribeSettingsRetainAsPublished =
                    userSettings.mqttSubscribeSettingsRetainAsPublished,
                subscribeRequestTopic = userSettings.mqttSubscribeRequestTopic,
                subscribeRequestQos = userSettings.mqttSubscribeRequestQos,
                subscribeRequestRetainHandling =
                    userSettings.mqttSubscribeRequestRetainHandling,
                subscribeRequestRetainAsPublished =
                    userSettings.mqttSubscribeRequestRetainAsPublished,
                willTopic = userSettings.mqttWillTopic,
                willPayload = userSettings.mqttWillPayload,
                willQos = userSettings.mqttWillQos,
                willRetain = userSettings.mqttWillRetain,
                willMessageExpiryInterval =
                    userSettings.mqttWillMessageExpiryInterval,
                willDelayInterval = userSettings.mqttWillDelayInterval,
                restrictionsReceiveMaximum =
                    userSettings.mqttRestrictionsReceiveMaximum,
                restrictionsSendMaximum = userSettings.mqttRestrictionsSendMaximum,
                restrictionsMaximumPacketSize =
                    userSettings.mqttRestrictionsMaximumPacketSize,
                restrictionsSendMaximumPacketSize =
                    userSettings.mqttRestrictionsSendMaximumPacketSize,
                restrictionsTopicAliasMaximum =
                    userSettings.mqttRestrictionsTopicAliasMaximum,
                restrictionsSendTopicAliasMaximum =
                    userSettings.mqttRestrictionsSendTopicAliasMaximum,
                restrictionsRequestProblemInformation =
                    userSettings.mqttRestrictionsRequestProblemInformation,
                restrictionsRequestResponseInformation =
                    userSettings.mqttRestrictionsRequestResponseInformation,
                homeAssistantEnabled = userSettings.mqttHomeAssistantEnabled,
                homeAssistantDiscoveryPrefix =
                    userSettings.mqttHomeAssistantDiscoveryPrefix,
                homeAssistantDeviceName = userSettings.mqttHomeAssistantDeviceName,
                homeAssistantStatusTopic = userSettings.mqttHomeAssistantStatusTopic,
            )
        if (rebuildClient) {
            pendingCancelConnect.set(false)
            reconnectCancellation = CompletableFuture()
            configurationError = null
            client =
                if (!config.enabled) {
                    null
                } else {
                    try {
                        require(
                            !config.homeAssistantEnabled ||
                                HomeAssistantDiscovery.isValidPrefix(
                                    config.homeAssistantDiscoveryPrefix
                                )
                        ) {
                            "Invalid Home Assistant discovery prefix."
                        }
                        require(
                            config.homeAssistantEnabled ||
                                isValidMqttPublishTopic(
                                    mqttVariableReplacement(config.willTopic)
                                )
                        ) {
                            "Invalid MQTT will topic after variable replacement."
                        }
                        buildClient(context)
                    } catch (e: Exception) {
                        configurationError =
                            e.message ?: "Invalid MQTT configuration."
                        addDebugLog("configuration invalid", configurationError)
                        Log.e(javaClass.simpleName, "Failed to build MQTT client", e)
                        null
                    }
                }
        }
    }

    @SuppressLint("NewApi")
    private fun buildClient(context: Context): Mqtt5AsyncClient {
        lateinit var builtClient: Mqtt5AsyncClient
        val stopReconnect = reconnectCancellation
        var builder =
            MqttClient.builder()
                .useMqttVersion5()
                .serverHost(config.serverHost)
                .serverPort(config.serverPort)

        if (config.useWebSocket) {
            builder =
                builder.webSocketConfig(
                    MqttWebSocketConfig.builder()
                        .subprotocol("mqtt")
                        .serverPath(
                            config.webSocketServerPath.trim().let { path ->
                                if (path.startsWith("/")) {
                                    path
                                } else {
                                    "/$path"
                                }
                            }
                        )
                        .build()
                )
        }

        if (config.useTls) {
            builder = builder.sslWithDefaultConfig()
        }

        if (config.clientId.isNotEmpty()) {
            builder = builder.identifier(mqttVariableReplacement(config.clientId))
        }

        builder =
            if (config.username.isNotEmpty() && config.password.isNotEmpty()) {
                builder
                    .simpleAuth()
                    .username(config.username)
                    .password(UTF_8.encode(config.password))
                    .applySimpleAuth()
            } else if (config.username.isNotEmpty()) {
                builder.simpleAuth().username(config.username).applySimpleAuth()
            } else if (config.password.isNotEmpty()) {
                builder
                    .simpleAuth()
                    .password(UTF_8.encode(config.password))
                    .applySimpleAuth()
            } else {
                builder
            }

        val ha =
            if (config.homeAssistantEnabled) {
                HomeAssistantDiscovery(
                    config.appInstanceId,
                    config.homeAssistantDiscoveryPrefix,
                    config.homeAssistantStatusTopic,
                )
            } else {
                null
            }
        var will =
            builder
                .willPublish()
                .topic(
                    ha?.availabilityTopic
                        ?: mqttVariableReplacement(config.willTopic)
                )
                .qos(
                    if (ha == null) {
                        config.willQos.toMqttQos()
                    } else {
                        MqttQosOption.AT_LEAST_ONCE.toMqttQos()
                    }
                )
                .retain(ha != null || config.willRetain)
                .delayInterval(
                    if (ha == null) {
                        config.willDelayInterval.toLong()
                    } else {
                        0L
                    }
                )
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .contentType("text/plain")
                .payload(
                    (if (ha == null) {
                            mqttVariableReplacement(config.willPayload)
                        } else {
                            "offline"
                        })
                        .toByteArray(UTF_8)
                )
                .userProperties()
                .add("username", config.username)
                .add("appInstanceId", config.appInstanceId)
                .applyUserProperties()
        if (ha == null) {
            will =
                will.messageExpiryInterval(config.willMessageExpiryInterval.toLong())
        }
        builder = will.applyWillPublish()

        return builder
            .addConnectedListener { connectedContext ->
                val c = builtClient
                if (
                    c !== client ||
                        pendingCancelConnect.get() ||
                        !config.enabled ||
                        !UserSettings(context).mqttEnabled
                ) {
                    val operation = pendingDisconnect
                    if (operation != null && operation.client === c) {
                        disconnectClient(operation)
                    } else {
                        cancelConnectedClient(c)
                    }
                    return@addConnectedListener
                }
                // HiveMQ automatically restores registered subscriptions on
                // reconnect.
                // Advance only the acknowledgement generation; original message
                // callbacks remain valid for that restored broker session.
                val epoch = subscriptionEpoch.incrementAndGet()
                homeAssistantSession?.stop()
                homeAssistantSession =
                    HomeAssistantMqttSession(
                            context.applicationContext,
                            c,
                            config,
                            scope,
                            isActive = {
                                getReadyClient() === c &&
                                    subscriptionEpoch.get() == epoch
                            },
                            log = { tag, message -> addDebugLog(tag, message) },
                        )
                        .also { it.start() }
                addDebugLog(
                    "connect success",
                    "Client ID: ${connectedContext.clientConfig.clientIdentifier.getOrNull()}",
                )
                publishEventMessage(
                    c,
                    OutboundConnectedEvent(
                        messageId = UUID.randomUUID().toString(),
                        username = config.username,
                        appInstanceId = config.appInstanceId,
                        data = getStatus(context),
                    ),
                )
            }
            .addDisconnectedListener { disconnectedContext ->
                homeAssistantSession
                    ?.takeIf { it.client === builtClient }
                    ?.let {
                        it.stop()
                        homeAssistantSession = null
                    }
                if (builtClient !== client) {
                    disconnectedContext.reconnector.reconnect(false)
                    return@addDisconnectedListener
                }
                if (pendingCancelConnect.get()) {
                    disconnectedContext.reconnector.reconnect(false)
                    return@addDisconnectedListener
                }
                if (
                    config.enabled &&
                        UserSettings(context).mqttEnabled &&
                        config.automaticReconnect &&
                        disconnectedContext.source != MqttDisconnectSource.USER
                ) {
                    val reconnectDelay = CompletableFuture<Void>()
                    val delayJob = scope.launch {
                        delay(
                            (Constants.MQTT_AUTO_RECONNECT_INTERVAL_SECONDS * 1_000L)
                                .milliseconds
                        )
                        reconnectDelay.complete(null)
                    }
                    val reconnectReady =
                        CompletableFuture.anyOf(reconnectDelay, stopReconnect)
                    reconnectReady.whenComplete { _, _ -> delayJob.cancel() }
                    disconnectedContext.reconnector.reconnectWhen(reconnectReady) {
                        _,
                        _ ->
                        // HiveMQ runs this callback on its event loop, where the
                        // reconnector may be updated before starting another
                        // attempt.
                        if (
                            builtClient !== client ||
                                pendingCancelConnect.get() ||
                                !config.enabled ||
                                !UserSettings(context).mqttEnabled
                        ) {
                            disconnectedContext.reconnector.reconnect(false)
                        }
                    }
                }

                val causeText = disconnectedContext.cause.message.orEmpty()
                val hint =
                    if (
                        causeText.contains(
                            "fixed header flags must be 0 but were 8",
                            ignoreCase = true,
                        )
                    ) {
                        "Hint: you may need to set 'MQTT -> Connection -> Use WebSocket' to true."
                    } else {
                        ""
                    }

                addDebugLog(
                    "disconnected",
                    """
                    source: ${disconnectedContext.source}
                    reconnect: ${disconnectedContext.reconnector.isReconnect}
                    cause: ${disconnectedContext.cause}

                    $hint
                    """
                        .trimIndent()
                        .trimEnd(),
                )
            }
            .transportConfig()
            .mqttConnectTimeout(config.mqttConnectTimeout.toLong(), TimeUnit.SECONDS)
            .socketConnectTimeout(
                config.socketConnectTimeout.toLong(),
                TimeUnit.SECONDS,
            )
            .applyTransportConfig()
            .buildAsync()
            .also { builtClient = it }
    }

    @Synchronized
    @SuppressLint("NewApi")
    fun connect(
        context: Context,
        onConnected: (() -> Unit)? = null,
        onError: ((String?) -> Unit)? = null,
    ) {
        val operation = pendingDisconnect
        if (operation != null) {
            val requestId = disconnectRequestId
            val appContext = context.applicationContext
            operation.completion.whenComplete { _, throwable ->
                synchronized(this) {
                    if (requestId != disconnectRequestId) {
                        onError?.invoke("MQTT connection cancelled.")
                    } else if (throwable != null) {
                        onError?.invoke(throwable.message)
                    } else {
                        connect(appContext, onConnected, onError)
                    }
                }
            }
            return
        }
        val existingClient = client
        if (
            existingClient != null &&
                existingClient.state != MqttClientState.DISCONNECTED
        ) {
            if (existingClient.state.isConnected && !pendingCancelConnect.get()) {
                onConnected?.invoke()
            } else {
                onError?.invoke(
                    "An MQTT connection or cancellation is already in progress."
                )
            }
            return
        }
        updateConfig(context.applicationContext)

        if (!config.enabled) {
            onError?.invoke("MQTT is not enabled in app settings.")
            addDebugLog("connect failed", "MQTT is not enabled in settings")
            return
        }
        val c = client
        if (c == null) {
            val message = configurationError ?: "MQTT client is not initialised."
            onError?.invoke(message)
            addDebugLog("connect failed", message)
            return
        }

        val websocketInfo =
            if (config.useWebSocket) {
                "yes (path: ${config.webSocketServerPath})"
            } else {
                "no"
            }
        addDebugLog(
            "connect pending...",
            """
                host: ${config.serverHost}
                port: ${config.serverPort}
                tls: ${
                    if (config.useTls) {
                        "yes"
                    } else {
                        "no"
                    }
                }
                websocket: $websocketInfo
                username: ${config.username}
            """
                .trimIndent(),
        )

        try {
            connectClient(c, context.applicationContext, onConnected, onError)
        } catch (e: Exception) {
            addDebugLog("connect failed", e.message)
            Log.e(javaClass.simpleName, "Failed to build MQTT connection", e)
            onError?.invoke(e.message)
        }
    }

    @Synchronized
    fun restart(
        context: Context,
        cause: OutboundDisconnectingEvent.DisconnectCause,
        onConnected: (() -> Unit)? = null,
        onError: ((String?) -> Unit)? = null,
    ) {
        val requestId = disconnectRequestId + 1
        val appContext = context.applicationContext
        disconnect(
            cause = cause,
            onDisconnected = {
                synchronized(this) {
                    if (requestId == disconnectRequestId) {
                        connect(appContext, onConnected, onError)
                    } else {
                        onError?.invoke("MQTT restart cancelled.")
                    }
                }
            },
            onError = onError,
        )
    }

    private fun connectClient(
        c: Mqtt5AsyncClient,
        context: Context,
        onConnected: (() -> Unit)?,
        onError: ((String?) -> Unit)?,
    ) {
        var connection =
            c.connectWith()
                .cleanStart(config.cleanStart)
                .keepAlive(config.keepAlive)
                .sessionExpiryInterval(config.sessionExpiryInterval.toLong())
                .userProperties()
                .add("username", config.username)
                .add("appInstanceId", config.appInstanceId)
                .applyUserProperties()

        var rb =
            connection
                .restrictions()
                .requestProblemInformation(
                    config.restrictionsRequestProblemInformation
                )
                .requestResponseInformation(
                    config.restrictionsRequestResponseInformation
                )

        if (config.restrictionsReceiveMaximum > 0) {
            rb = rb.receiveMaximum(config.restrictionsReceiveMaximum)
        }
        if (config.restrictionsSendMaximum > 0) {
            rb = rb.sendMaximum(config.restrictionsSendMaximum)
        }
        if (config.restrictionsMaximumPacketSize > 0) {
            rb = rb.maximumPacketSize(config.restrictionsMaximumPacketSize)
        }
        if (config.restrictionsSendMaximumPacketSize > 0) {
            rb = rb.sendMaximumPacketSize(config.restrictionsSendMaximumPacketSize)
        }
        if (config.restrictionsTopicAliasMaximum > 0) {
            rb = rb.topicAliasMaximum(config.restrictionsTopicAliasMaximum)
        }
        if (config.restrictionsSendTopicAliasMaximum > 0) {
            rb = rb.sendTopicAliasMaximum(config.restrictionsSendTopicAliasMaximum)
        }

        connection = rb.applyRestrictions()

        @SuppressLint("NewApi")
        connection.send().whenComplete { _, throwable ->
            handleConnectResult(
                c,
                throwable,
                onConnected,
                onError,
                { subscribeToTopics(c) },
            ) {
                UserSettings(context).mqttEnabled
            }
        }
    }

    /**
     * Handles a completed MQTT connection attempt. The CompletableFuture stays in
     * connectClient because Android Retrofix rewrites its JVM type at runtime.
     */
    internal fun handleConnectResult(
        c: Mqtt5AsyncClient,
        failure: Throwable?,
        onConnected: (() -> Unit)?,
        onError: ((String?) -> Unit)?,
        subscribe: () -> Unit,
        isEnabled: () -> Boolean = { config.enabled },
    ) {
        if (failure == null) {
            if (!isEnabled() || getReadyClient() !== c) {
                onError?.invoke("MQTT connection cancelled.")
                return
            }
            try {
                subscribe()
            } catch (e: Exception) {
                addDebugLog("subscribe failed", e.message)
                Log.e(
                    javaClass.simpleName,
                    "Failed to subscribe after MQTT connection",
                    e,
                )
                onError?.invoke(e.message)
                return
            }
            // Subscription setup can race with a stop, disable or replacement.
            // Never report a successful connection for a client lost meanwhile.
            if (!isEnabled() || getReadyClient() !== c) {
                onError?.invoke("MQTT connection cancelled.")
                return
            }
            onConnected?.invoke()
        } else {
            addDebugLog("connect failed", failure.message)
            Log.e(javaClass.simpleName, "Failed to subscribe/connect", failure)
            onError?.invoke(failure.message)
        }
    }

    fun publishUrlChangedEvent(url: String) {
        val c = getReadyClient() ?: return
        val event =
            OutboundUrlChangedEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
                data = OutboundUrlChangedEvent.UrlData(url),
            )
        publishEventMessage(c, event)
    }

    fun publishLockEvent(lockStateType: LockStateType) {
        val c = getReadyClient() ?: return
        val event =
            OutboundLockEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
                data = OutboundLockEvent.LockStateData(lockStateType),
            )
        publishEventMessage(c, event)
    }

    fun publishUnlockEvent() {
        val c = getReadyClient() ?: return
        val event =
            OutboundUnlockEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
            )
        publishEventMessage(c, event)
    }

    fun publishAppForegroundEvent() {
        val c = getReadyClient() ?: return
        val event =
            OutboundAppForegroundEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
            )
        publishEventMessage(c, event)
    }

    fun publishAppBackgroundEvent() {
        val c = getReadyClient() ?: return
        val event =
            OutboundAppBackgroundEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
            )
        publishEventMessage(c, event)
    }

    fun publishScreenOnEvent() {
        val c = getReadyClient() ?: return
        val event =
            OutboundScreenOnEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
            )
        publishEventMessage(c, event)
    }

    fun publishScreenOffEvent() {
        val c = getReadyClient() ?: return
        val event =
            OutboundScreenOffEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
            )
        publishEventMessage(c, event)
    }

    fun publishUserPresentEvent() {
        val c = getReadyClient() ?: return
        val event =
            OutboundUserPresentEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
            )
        publishEventMessage(c, event)
    }

    fun publishPowerPluggedEvent() {
        val c = getReadyClient() ?: return
        val event =
            OutboundPowerPluggedEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
            )
        publishEventMessage(c, event)
    }

    fun publishPowerUnpluggedEvent() {
        val c = getReadyClient() ?: return
        val event =
            OutboundPowerUnpluggedEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
            )
        publishEventMessage(c, event)
    }

    fun publishApplicationRestrictionsChangedEvent() {
        val c = getReadyClient() ?: return
        val event =
            OutboundApplicationRestrictionsChangedEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
            )
        publishEventMessage(c, event)
    }

    private fun publishEventMessage(
        c: Mqtt5AsyncClient,
        event: OutboundEventMessage,
        whenComplete: ((Mqtt5PublishResult?, Throwable?) -> Unit)? = null,
    ) {
        homeAssistantSession?.takeIf { it.client === c }?.onEvent(event)
        val payload = OutboundEventJsonParser.encodeToString(event)
        val topic =
            mqttVariableReplacement(
                config.publishEventTopic,
                mapOf(MqttVariableName.EVENT_TYPE.name to event.getEventType()),
            )
        publishToMqtt(
            c,
            topic,
            payload,
            config.publishEventQos,
            config.publishEventRetain,
            messageId = event.messageId,
            whenComplete = whenComplete,
        )
    }

    fun publishStatusResponse(
        statusRequest: InboundStatusRequest,
        status: WebviewKioskStatus,
    ) {
        val c = getReadyClient() ?: return
        val statusMessage =
            OutboundStatusResponse(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
                requestMessageId = statusRequest.messageId,
                correlationData = statusRequest.correlationData,
                data = status,
            )
        publishResponseMessage(
            c,
            statusMessage,
            statusRequest,
        )
    }

    fun publishSettingsResponse(
        settingsRequest: InboundSettingsRequest,
        settings: JSONObject,
    ) {
        val c = getReadyClient() ?: return
        val settingsMessage =
            OutboundSettingsResponse(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
                requestMessageId = settingsRequest.messageId,
                correlationData = settingsRequest.correlationData,
                data =
                    OutboundSettingsResponse.SettingsResponseData(
                        filterSettingsJson(settings, settingsRequest.data.settings)
                    ),
            )
        publishResponseMessage(
            c,
            settingsMessage,
            settingsRequest,
        )
    }

    fun publishSystemInfoResponse(
        systemInfoRequest: InboundSystemInfoRequest,
        systemInfo: SystemInfo,
    ) {
        val c = getReadyClient() ?: return
        val statusMessage =
            OutboundSystemInfoResponse(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
                requestMessageId = systemInfoRequest.messageId,
                correlationData = systemInfoRequest.correlationData,
                data = systemInfo,
            )
        publishResponseMessage(
            c,
            statusMessage,
            systemInfoRequest,
        )
    }

    fun publishLaunchablePackagesResponse(
        request: InboundLaunchablePackagesRequest,
        packages: List<String>,
    ) {
        val c = getReadyClient() ?: return
        val message =
            OutboundLaunchablePackagesResponse(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
                requestMessageId = request.messageId,
                correlationData = request.correlationData,
                data = OutboundLaunchablePackagesResponse.Data(packages),
            )
        publishResponseMessage(
            c,
            message,
            request,
        )
    }

    fun publishLockTaskPermittedPackagesResponse(
        request: InboundLockTaskPackagesRequest,
        packages: List<String>,
    ) {
        val c = getReadyClient() ?: return
        val message =
            OutboundLockTaskPackagesResponse(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
                requestMessageId = request.messageId,
                correlationData = request.correlationData,
                data = OutboundLockTaskPackagesResponse.Data(packages),
            )
        publishResponseMessage(
            c,
            message,
            request,
        )
    }

    fun publishErrorResponse(errorRequest: InboundErrorRequest) {
        val c = getReadyClient() ?: return
        val errorMessage =
            OutboundErrorResponse(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
                requestMessageId = errorRequest.messageId,
                correlationData = errorRequest.correlationData,
                payloadStr = errorRequest.payloadStr,
                errorMessage = errorRequest.error,
            )
        publishResponseMessage(
            c,
            errorMessage,
            errorRequest,
        )
    }

    private fun publishResponseMessage(
        c: Mqtt5AsyncClient,
        responseMessage: OutboundResponseMessage,
        requestMessage: InboundRequestMessage,
    ) {
        if (
            homeAssistantSession
                ?.takeIf { it.client === c }
                ?.onResponse(responseMessage, requestMessage) == true
        ) {
            return
        }
        val topic =
            mqttVariableReplacement(
                requestMessage.responseTopic.takeIf { !it.isNullOrEmpty() }
                    ?: config.publishResponseTopic,
                mapOf(
                    MqttVariableName.RESPONSE_TYPE.name to responseMessage.getType()
                ),
            )

        val payload = OutboundResponseJsonParser.encodeToString(responseMessage)
        publishToMqtt(
            c,
            topic,
            payload,
            config.publishResponseQos,
            config.publishResponseRetain,
            correlationData = requestMessage.correlationData?.toByteArray(),
            messageId = responseMessage.messageId,
        )
    }

    private fun publishToMqtt(
        c: Mqtt5AsyncClient,
        topic: String,
        payload: String,
        qos: MqttQosOption,
        retain: Boolean,
        correlationData: ByteArray? = null,
        messageId: String? = null,
        whenComplete: ((Mqtt5PublishResult?, Throwable?) -> Unit)? = null,
    ) {
        if (!isValidMqttPublishTopic(topic)) {
            val error = IllegalArgumentException("Invalid publish topic name.")
            addDebugLog(
                "publish failed",
                "topic: $topic\nerror: Invalid publish topic name.",
                messageId,
            )
            whenComplete?.invoke(null, error)
            return
        }

        // A completion can arrive after this client was replaced, disconnected, or
        // transparently reconnected with a different broker session.
        val publishingEpoch = subscriptionEpoch.get()
        try {
            @SuppressLint("NewApi")
            c.publishWith()
                .topic(topic)
                .correlationData(correlationData)
                .qos(qos.toMqttQos())
                .retain(retain)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .contentType("application/json")
                .payload(payload.toByteArray())
                .userProperties()
                .add("username", config.username)
                .add("appInstanceId", config.appInstanceId)
                .applyUserProperties()
                .send()
                .whenComplete { result, throwable ->
                    // Internal shutdown progress must be delivered even if the
                    // publish result no longer belongs to the active session.
                    whenComplete?.invoke(result, throwable)
                    if (
                        getReadyClient() !== c ||
                            subscriptionEpoch.get() != publishingEpoch
                    ) {
                        return@whenComplete
                    }
                    if (throwable == null) {
                        addDebugLog(
                            "publish success",
                            "topic: $topic\npayload: $payload",
                            messageId,
                        )
                    } else {
                        addDebugLog(
                            "publish error",
                            "topic: $topic\nerror: $throwable",
                            messageId,
                        )
                    }
                }
        } catch (e: Exception) {
            addDebugLog(
                "publish failed",
                "topic: $topic\nerror: $e",
                messageId,
            )
            whenComplete?.invoke(null, e)
        }
    }

    // Capture the owning client before the batch. A concurrent replacement must not
    // receive leftover topics from a superseded connect attempt.
    // Invoked reflectively by MQTT operation-state regression tests.
    @Suppress("unused")
    private fun subscribeToTopics() {
        val subscribingClient = getReadyClient() ?: return
        subscribeToTopics(subscribingClient)
    }

    private fun subscribeToTopics(subscribingClient: Mqtt5AsyncClient) {
        val epoch = subscriptionEpoch.get()
        homeAssistantSession?.discovery?.let { ha ->
            subscribeTopic(
                subscribingClient,
                epoch,
                ha.birthTopic,
                MqttQosOption.AT_LEAST_ONCE,
                MqttRetainHandlingOption.SEND,
                false,
            ) { _, payload ->
                if (payload == "online") {
                    homeAssistantSession
                        ?.takeIf { it.client === subscribingClient }
                        ?.onHomeAssistantBirth()
                }
            }
            ha.controlTopics.forEach { topic ->
                subscribeTopic(
                    subscribingClient,
                    epoch,
                    topic,
                    MqttQosOption.AT_LEAST_ONCE,
                    MqttRetainHandlingOption.DO_NOT_SEND,
                    true,
                ) { publish, payload ->
                    // Retained control messages must not execute after a reconnect.
                    if (!publish.isRetain) {
                        when (topic) {
                            ha.commandTopic -> handleCommandMessage(publish, payload)
                            ha.settingsCommandTopic ->
                                handleSettingsMessage(publish, payload)
                            ha.requestTopic ->
                                handleRequestMessage(publish, payload, ha)
                        }
                    }
                }
            }
        }
        val haControlTopics =
            homeAssistantSession?.discovery?.controlTopics.orEmpty()
        if (
            mqttVariableReplacement(config.subscribeCommandTopic) !in haControlTopics
        ) {
            subscribeTopic(
                subscribingClient,
                epoch = epoch,
                topic = config.subscribeCommandTopic,
                qos = config.subscribeCommandQos,
                retainHandling = config.subscribeCommandRetainHandling,
                retainAsPublished = config.subscribeCommandRetainAsPublished,
            ) { publish, payload ->
                // A wildcard command subscription can also match the dedicated
                // HA topic. Its protected callback owns that delivery.
                if (publish.topic.toString() !in haControlTopics) {
                    handleCommandMessage(publish, payload)
                }
            }
        }

        if (
            mqttVariableReplacement(config.subscribeSettingsTopic) !in
                haControlTopics
        ) {
            subscribeTopic(
                subscribingClient,
                epoch,
                config.subscribeSettingsTopic,
                config.subscribeSettingsQos,
                config.subscribeSettingsRetainHandling,
                config.subscribeSettingsRetainAsPublished,
            ) { publish, payload ->
                if (publish.topic.toString() !in haControlTopics) {
                    handleSettingsMessage(publish, payload)
                }
            }
        }
        if (
            mqttVariableReplacement(config.subscribeRequestTopic) !in haControlTopics
        ) {
            subscribeTopic(
                subscribingClient,
                epoch,
                config.subscribeRequestTopic,
                config.subscribeRequestQos,
                config.subscribeRequestRetainHandling,
                config.subscribeRequestRetainAsPublished,
            ) { publish, payload ->
                if (publish.topic.toString() !in haControlTopics) {
                    handleRequestMessage(publish, payload)
                }
            }
        }
    }

    private fun handleSettingsMessage(publish: Mqtt5Publish, payloadStr: String) {
        val settingsMessage = runCatching {
            BaseJson.decodeFromString<InboundSettingsMessage>(payloadStr)
        }
            .getOrElse { e ->
                addDebugLog(
                    "settings error",
                    e.message,
                    messageId = getValueFromPrimitiveJson(payloadStr, "messageId"),
                )
                return
            }

        val targetInstances = settingsMessage.targetInstances
        val targetUsernames = settingsMessage.targetUsernames
        if (
            (targetInstances.isNullOrEmpty() ||
                targetInstances.contains(config.appInstanceId)) &&
                (targetUsernames.isNullOrEmpty() ||
                    targetUsernames.contains(config.username))
        ) {
            addDebugLog(
                "settings received",
                "topic: ${publish.topic}",
                messageId = settingsMessage.messageId,
            )
            RemoteMessageManager.emitSettings(
                settingsMessage,
                RemoteMessageManager.RemoteMessage.Source.MQTT,
            )
        } else {
            addDebugLog(
                "settings received (ignored)",
                "topic: ${publish.topic}",
                messageId = settingsMessage.messageId,
            )
        }
    }

    private fun handleRequestMessage(
        publish: Mqtt5Publish,
        payloadStr: String,
        homeAssistant: HomeAssistantDiscovery? = null,
    ) {
        @SuppressLint("NewApi")
        val responseTopic =
            publish.responseTopic.takeIf { it.isPresent }?.get()?.toString()
        @SuppressLint("NewApi")
        val correlationData =
            publish.correlationData
                .takeIf { it.isPresent }
                ?.let { buf ->
                    val bytes =
                        ByteArray(buf.get().remaining()).also {
                            buf.get().get(it)
                        }
                    String(bytes, UTF_8)
                }

        try {
            val request =
                InboundRequestJsonParser.decodeFromString<InboundRequestMessage>(
                    payloadStr
                )

            val targetInstances = request.targetInstances
            val targetUsernames = request.targetUsernames
            if (
                (targetInstances.isNullOrEmpty() ||
                    targetInstances.contains(config.appInstanceId)) &&
                    (targetUsernames.isNullOrEmpty() ||
                        targetUsernames.contains(config.username))
            ) {
                request.responseTopic =
                    if (homeAssistant == null) {
                        responseTopic ?: request.responseTopic
                    } else {
                        val requestType =
                            InboundRequestJsonParser.encodeToJsonElement<
                                    InboundRequestMessage
                                >(
                                    request
                                )
                                .jsonObject
                                .getValue("requestType")
                                .jsonPrimitive
                                .content
                        homeAssistant.responseTopic(requestType)
                    }
                request.correlationData = correlationData ?: request.correlationData

                addDebugLog(
                    "request received",
                    "topic: ${publish.topic}\nrequest: $request",
                    request.messageId,
                )
                RemoteMessageManager.emitRequest(
                    request,
                    RemoteMessageManager.RemoteMessage.Source.MQTT,
                )
            } else {
                addDebugLog(
                    "request received (ignored)",
                    "topic: ${publish.topic}\nrequest: $request",
                    request.messageId,
                )
            }
        } catch (e: Exception) {
            val messageId = getValueFromPrimitiveJson(payloadStr, "messageId")
            val errorRequest =
                InboundErrorRequest(
                    messageId = messageId,
                    responseTopic =
                        homeAssistant?.responseTopic("error")
                            ?: responseTopic
                            ?: getValueFromPrimitiveJson(
                                payloadStr,
                                "responseTopic",
                            ),
                    correlationData =
                        correlationData
                            ?: getValueFromPrimitiveJson(
                                payloadStr,
                                "correlationData",
                            ),
                    targetInstances = runCatching {
                            Json.parseToJsonElement(payloadStr)
                                .jsonObject["targetInstances"]
                                ?.jsonArray
                                ?.mapNotNull {
                                    it.jsonPrimitive.contentOrNull
                                }
                                ?.toSet()
                        }
                            .getOrNull(),
                    payloadStr = payloadStr,
                    error = e.message ?: e.toString(),
                )
            RemoteMessageManager.emitRequest(
                errorRequest,
                RemoteMessageManager.RemoteMessage.Source.MQTT,
            )
            addDebugLog("request error", e.message, messageId)
        }
    }

    private fun handleCommandMessage(publish: Mqtt5Publish, payloadStr: String) {
        try {
            val command =
                InboundCommandJsonParser.decodeFromString<InboundCommandMessage>(
                    payloadStr
                )
            val targetInstances = command.targetInstances
            val targetUsernames = command.targetUsernames
            if (
                (targetInstances.isNullOrEmpty() ||
                    targetInstances.contains(config.appInstanceId)) &&
                    (targetUsernames.isNullOrEmpty() ||
                        targetUsernames.contains(config.username))
            ) {
                addDebugLog(
                    "command received",
                    "topic: ${publish.topic}\ncommand: $command",
                    command.messageId,
                )
                RemoteMessageManager.emitCommand(
                    command,
                    RemoteMessageManager.RemoteMessage.Source.MQTT,
                )
            } else {
                addDebugLog(
                    "command received (ignored)",
                    "topic: ${publish.topic}\ncommand: $command",
                    command.messageId,
                )
            }
        } catch (e: Exception) {
            RemoteMessageManager.emitCommand(
                InboundErrorCommand(e.message ?: e.toString()),
                RemoteMessageManager.RemoteMessage.Source.MQTT,
            )
            val messageId = getValueFromPrimitiveJson(payloadStr, "messageId")
            addDebugLog("command error", e.message, messageId)
        }
    }

    private fun getValueFromPrimitiveJson(payloadStr: String, key: String): String? {
        return runCatching {
            Json.parseToJsonElement(payloadStr)
                .jsonObject[key]
                ?.jsonPrimitive
                ?.contentOrNull
        }
            .getOrNull()
    }

    private fun getReadyClient(): Mqtt5AsyncClient? {
        val c = client
        if (
            c != null &&
                ::config.isInitialized &&
                c.state.isConnected &&
                config.enabled &&
                !pendingCancelConnect.get() &&
                pendingDisconnect == null &&
                c === client
        ) {
            return c
        }
        return null
    }

    /** The HiveMQ callback is retained across automatic reconnect of this client. */
    internal fun dispatchSubscriptionIfActive(
        c: Mqtt5AsyncClient,
        dispatch: () -> Unit,
    ) {
        if (getReadyClient() === c) {
            dispatch()
        }
    }

    internal fun handleSubscriptionAck(
        c: Mqtt5AsyncClient,
        epoch: Long,
        topic: String,
        ack: Mqtt5SubAck?,
        failure: Throwable?,
    ) {
        if (getReadyClient() !== c || subscriptionEpoch.get() != epoch) {
            return
        }
        val rejected = ack?.reasonCodes?.filter { it.code >= 0x80 }.orEmpty()
        when {
            failure != null ->
                addDebugLog("subscribe error", "topic: $topic\nerror: $failure")
            ack == null ->
                addDebugLog(
                    "subscribe error",
                    "topic: $topic\nerror: Missing SUBACK",
                )
            rejected.isNotEmpty() ->
                addDebugLog(
                    "subscribe error",
                    "topic: $topic\nrejected reason codes: ${rejected.joinToString()}",
                )
            else -> addDebugLog("subscribe success", "topic: $topic")
        }
    }

    private fun subscribeTopic(
        c: Mqtt5AsyncClient,
        epoch: Long,
        topic: String,
        qos: MqttQosOption,
        retainHandling: MqttRetainHandlingOption,
        retainAsPublished: Boolean,
        onMessage: (publish: Mqtt5Publish, payloadStr: String) -> Unit,
    ) {
        if (getReadyClient() !== c || subscriptionEpoch.get() != epoch) {
            return
        }
        val subscribeTopic = mqttVariableReplacement(topic)
        if (!isValidMqttSubscribeTopic(subscribeTopic)) {
            addDebugLog(
                "subscribe failed",
                "topic: $subscribeTopic\nerror: Invalid topic name",
            )
            return
        }
        try {
            @SuppressLint("NewApi")
            c.subscribeWith()
                .topicFilter(subscribeTopic)
                .qos(qos.toMqttQos())
                .retainHandling(retainHandling.toMqttRetainHandling())
                .retainAsPublished(retainAsPublished)
                .noLocal(true)
                .userProperties()
                .add("username", config.username)
                .add("appInstanceId", config.appInstanceId)
                .applyUserProperties()
                .callback { publish ->
                    dispatchSubscriptionIfActive(c) {
                        val payloadStr = publish.payloadAsBytes.toString(UTF_8)
                        onMessage(publish, payloadStr)
                    }
                }
                .send()
                .whenComplete { ack, throwable ->
                    handleSubscriptionAck(c, epoch, subscribeTopic, ack, throwable)
                }
        } catch (e: Exception) {
            addDebugLog("subscribe failed", "topic: $subscribeTopic\nerror: $e")
            Log.e(
                javaClass.simpleName,
                "Failed to subscribe to topic $subscribeTopic",
                e,
            )
        }
    }

    fun isInitialized(): Boolean = ::config.isInitialized

    fun isConnected(): Boolean = client?.state?.isConnected ?: false

    fun getState() = client?.state ?: MqttClientState.DISCONNECTED

    @Synchronized
    fun cancelConnect(): Boolean {
        val c = client ?: return false
        if (pendingDisconnect != null) {
            // Also cancel a foreground connect/restart queued behind this shutdown.
            disconnectRequestId++
            return false
        }
        if (c.state == MqttClientState.DISCONNECTED || pendingCancelConnect.get()) {
            return false
        }
        addDebugLog(
            "connect cancel requested",
            "User manually triggered cancellation request.",
        )
        disconnect(
            OutboundDisconnectingEvent.DisconnectCause.USER_INITIATED_DISCONNECT
        )
        return true
    }

    private fun cancelConnectedClient(c: Mqtt5AsyncClient) {
        try {
            @SuppressLint("NewApi")
            c.disconnect().whenComplete { _, throwable ->
                if (throwable != null) {
                    addDebugLog("connect cancellation failed", throwable.message)
                }
            }
        } catch (e: Exception) {
            addDebugLog("connect cancellation failed", e.message)
            Log.e(javaClass.simpleName, "Failed to cancel MQTT connection", e)
        }
    }

    @SuppressLint("NewApi")
    private fun finishDisconnect(
        operation: DisconnectOperation,
        error: Throwable? = null,
    ) {
        synchronized(this) {
            if (pendingDisconnect !== operation) {
                return
            }
            pendingDisconnect = null
            if (operation.client.state == MqttClientState.DISCONNECTED) {
                pendingCancelConnect.set(false)
            }
        }
        if (error == null) {
            operation.completion.complete(null)
        } else {
            addDebugLog("disconnect failed", error.message)
            operation.completion.completeExceptionally(error)
        }
    }

    @Synchronized
    private fun disconnectClient(operation: DisconnectOperation) {
        // The event PUBACK or its timeout can outlive a shutdown. A reconnect
        // may reuse the same HiveMQ client, so checking its state alone would
        // disconnect the *new* session on behalf of a retired operation.
        // finishDisconnect clears the operation under this same monitor before
        // completing its future; ownership therefore also guards completion.
        // Avoid CompletableFuture.isDone (API 24) on the API 21+ code path.
        if (pendingDisconnect !== operation) {
            return
        }
        val c = operation.client
        if (!c.state.isConnected || !operation.started.compareAndSet(false, true)) {
            return
        }
        try {
            var disconnect = c.disconnectWith()
            if (
                homeAssistantSession?.takeIf { it.client === c }?.discovery != null
            ) {
                // Also cover the shutdown timeout/failure path: a clean MQTT
                // disconnect would otherwise suppress the availability Last Will.
                disconnect =
                    disconnect.reasonCode(
                        Mqtt5DisconnectReasonCode.DISCONNECT_WITH_WILL_MESSAGE
                    )
            }
            @SuppressLint("NewApi")
            disconnect
                .userProperties()
                .add("username", config.username)
                .add("appInstanceId", config.appInstanceId)
                .applyUserProperties()
                .send()
                .whenComplete { _, throwable ->
                    finishDisconnect(
                        operation,
                        if (c.state == MqttClientState.DISCONNECTED) {
                            null
                        } else {
                            throwable
                                ?: IllegalStateException(
                                    "MQTT disconnect completed while the client is still connected."
                                )
                        },
                    )
                }
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "Failed to build MQTT disconnect", e)
            finishDisconnect(operation, e)
        }
    }

    @Synchronized
    @SuppressLint("NewApi")
    fun disconnect(
        cause: OutboundDisconnectingEvent.DisconnectCause,
        onDisconnected: (() -> Unit)? = null,
        onError: ((String?) -> Unit)? = null,
    ) {
        // A later stop/disable cancels any connect deferred by an earlier foreground
        // start.
        disconnectRequestId++
        val existingOperation = pendingDisconnect
        val c = existingOperation?.client ?: client
        if (
            c == null ||
                (existingOperation == null &&
                    c.state == MqttClientState.DISCONNECTED)
        ) {
            addDebugLog("disconnect - already disconnected")
            onDisconnected?.invoke()
            return
        }

        val operation =
            existingOperation
                ?: DisconnectOperation(c).also {
                    pendingDisconnect = it
                    pendingCancelConnect.set(true)
                    reconnectCancellation.complete(null)
                }
        operation.completion.whenComplete { _, throwable ->
            if (throwable == null) {
                onDisconnected?.invoke()
            } else {
                onError?.invoke(throwable.message)
            }
        }
        if (existingOperation != null) {
            return
        }

        // A connection attempt can only be closed once it finishes or fails. Its
        // listeners suppress retries and close a late success before it is accepted.
        scope.launch {
            while (
                !operation.completion.isDone &&
                    c.state != MqttClientState.DISCONNECTED
            ) {
                delay(25.milliseconds)
            }
            if (!operation.completion.isDone) {
                finishDisconnect(operation)
            }
        }
        if (!c.state.isConnected) {
            return
        }

        val eventTimeout = scope.launch {
            delay(DISCONNECT_EVENT_TIMEOUT_MS.milliseconds)
            if (synchronized(this@MqttManager) { pendingDisconnect !== operation }) {
                return@launch
            }
            addDebugLog(
                "disconnect event timed out",
                "Disconnecting without waiting for the event acknowledgement.",
            )
            disconnectClient(operation)
        }
        // Cancellation also covers shutdowns that completed while the PUBACK
        // was still pending (the normal acknowledgement callback never ran).
        operation.completion.whenComplete { _, _ -> eventTimeout.cancel() }
        try {
            publishEventMessage(
                c,
                OutboundDisconnectingEvent(
                    messageId = UUID.randomUUID().toString(),
                    username = config.username,
                    appInstanceId = config.appInstanceId,
                    data = OutboundDisconnectingEvent.DisconnectingData(cause),
                ),
                whenComplete = { _, _ ->
                    synchronized(this) {
                        if (pendingDisconnect !== operation) {
                            return@publishEventMessage
                        }
                        val session = homeAssistantSession?.takeIf {
                            it.client === c
                        }
                        if (session == null) {
                            eventTimeout.cancel()
                            disconnectClient(operation)
                        } else {
                            session.disconnect {
                                eventTimeout.cancel()
                                disconnectClient(operation)
                            }
                        }
                    }
                },
            )
        } catch (e: Exception) {
            eventTimeout.cancel()
            addDebugLog("disconnect event failed", e.message)
            Log.e(javaClass.simpleName, "Failed to publish MQTT disconnect event", e)
            disconnectClient(operation)
        }
    }

    fun clearLogs() {
        synchronized(logHistory) {
            logHistory.clear()
        }
    }

    fun mqttVariableReplacement(
        value: String,
        additionalReplacementMap: Map<String, String> = emptyMap(),
    ): String {
        val variableReplacementMap =
            mapOf(
                MqttVariableName.APP_INSTANCE_ID.name to config.appInstanceId,
                MqttVariableName.USERNAME.name to config.username,
            ) + additionalReplacementMap

        return replaceVariables(value, variableReplacementMap)
    }
}
