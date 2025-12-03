package uk.nktnet.webviewkiosk.mqtt

import uk.nktnet.webviewkiosk.mqtt.messages.MqttSettingsMessage
import android.annotation.SuppressLint
import android.content.Context
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.MqttWebSocketConfig
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.LockStateType
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.config.option.MqttRetainHandlingOption
import uk.nktnet.webviewkiosk.config.option.MqttVariableNameOption
import uk.nktnet.webviewkiosk.mqtt.messages.MqttCommandJsonParser
import uk.nktnet.webviewkiosk.mqtt.messages.MqttCommandMessage
import uk.nktnet.webviewkiosk.mqtt.messages.MqttConnectedEvent
import uk.nktnet.webviewkiosk.mqtt.messages.MqttDisconnectingEvent
import uk.nktnet.webviewkiosk.mqtt.messages.MqttErrorResponse
import uk.nktnet.webviewkiosk.mqtt.messages.MqttEventJsonParser
import uk.nktnet.webviewkiosk.mqtt.messages.MqttEventMessage
import uk.nktnet.webviewkiosk.mqtt.messages.MqttSettingsRequest
import uk.nktnet.webviewkiosk.mqtt.messages.MqttStatusRequest
import uk.nktnet.webviewkiosk.mqtt.messages.MqttSystemInfoRequest
import uk.nktnet.webviewkiosk.mqtt.messages.MqttLockEvent
import uk.nktnet.webviewkiosk.mqtt.messages.MqttErrorCommand
import uk.nktnet.webviewkiosk.mqtt.messages.MqttErrorRequest
import uk.nktnet.webviewkiosk.mqtt.messages.MqttRequestJsonParser
import uk.nktnet.webviewkiosk.mqtt.messages.MqttRequestMessage
import uk.nktnet.webviewkiosk.mqtt.messages.MqttResponseJsonParser
import uk.nktnet.webviewkiosk.mqtt.messages.MqttResponseMessage
import uk.nktnet.webviewkiosk.mqtt.messages.MqttSettingsResponse
import uk.nktnet.webviewkiosk.mqtt.messages.MqttStatusResponse
import uk.nktnet.webviewkiosk.mqtt.messages.MqttSystemInfoResponse
import uk.nktnet.webviewkiosk.mqtt.messages.MqttUnlockEvent
import uk.nktnet.webviewkiosk.mqtt.messages.MqttUrlChangedEvent
import uk.nktnet.webviewkiosk.utils.SystemInfo
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus
import uk.nktnet.webviewkiosk.utils.filterSettingsJson
import uk.nktnet.webviewkiosk.utils.getStatus
import uk.nktnet.webviewkiosk.utils.isValidMqttPublishTopic
import uk.nktnet.webviewkiosk.utils.isValidMqttSubscribeTopic
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.jvm.optionals.getOrNull
import kotlin.text.Charsets.UTF_8

data class MqttLogEntry(
    val timestamp: Date,
    val tag: String,
    val message: String?,
    val messageId: String?,
)

object MqttManager {
    private var client: Mqtt5AsyncClient? = null
    private lateinit var config: MqttConfig

    private val scope = CoroutineScope(Dispatchers.Default)
    private val _commands = MutableSharedFlow<MqttCommandMessage>(extraBufferCapacity = 100)
    val commands: SharedFlow<MqttCommandMessage> get() = _commands

    private val _settings = MutableSharedFlow<MqttSettingsMessage>(extraBufferCapacity = 100)
    val settings: SharedFlow<MqttSettingsMessage> get() = _settings

    private val _requests = MutableSharedFlow<MqttRequestMessage>(extraBufferCapacity = 100)
    val requests: SharedFlow<MqttRequestMessage> get() = _requests

    private val logHistory = ArrayDeque<MqttLogEntry>(100)
    private val _debugLog = MutableSharedFlow<MqttLogEntry>(extraBufferCapacity = 100)
    val debugLog: SharedFlow<MqttLogEntry> get() = _debugLog
    private val pendingCancelConnect: AtomicBoolean = AtomicBoolean(false)

    private fun addDebugLog(tag: String, message: String? = null, messageId: String? = null) {
        val logEntry = MqttLogEntry(Date(), tag, message, messageId)
        synchronized(logHistory) {
            if (logHistory.size >= 100) logHistory.removeFirst()
            logHistory.addLast(logEntry)
        }
        scope.launch {
            _debugLog.emit(logEntry)
        }
    }

    val debugLogHistory: List<MqttLogEntry>
        get() = synchronized(logHistory) { logHistory.toList() }

    fun updateConfig(
        systemSettings: SystemSettings,
        userSettings: UserSettings,
        rebuildClient: Boolean = true
    ) {
        config = MqttConfig(
            appInstanceId = systemSettings.appInstanceId,

            enabled = userSettings.mqttEnabled,
            clientId = userSettings.mqttClientId,
            serverHost = userSettings.mqttServerHost,
            serverPort = userSettings.mqttServerPort,
            username = userSettings.mqttUsername,
            password = userSettings.mqttPassword,
            useTls = userSettings.mqttUseTls,
            automaticReconnect = userSettings.mqttAutomaticReconnect,
            cleanStart = userSettings.mqttCleanStart,
            keepAlive = userSettings.mqttKeepAlive,
            mqttConnectTimeout = userSettings.mqttConnectTimeout,
            socketConnectTimeout = userSettings.mqttSocketConnectTimeout,
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
            subscribeCommandRetainHandling = userSettings.mqttSubscribeCommandRetainHandling,
            subscribeCommandRetainAsPublished = userSettings.mqttSubscribeCommandRetainAsPublished,

            subscribeSettingsTopic = userSettings.mqttSubscribeSettingsTopic,
            subscribeSettingsQos = userSettings.mqttSubscribeSettingsQos,
            subscribeSettingsRetainHandling = userSettings.mqttSubscribeSettingsRetainHandling,
            subscribeSettingsRetainAsPublished = userSettings.mqttSubscribeSettingsRetainAsPublished,

            subscribeRequestTopic = userSettings.mqttSubscribeRequestTopic,
            subscribeRequestQos = userSettings.mqttSubscribeRequestQos,
            subscribeRequestRetainHandling = userSettings.mqttSubscribeRequestRetainHandling,
            subscribeRequestRetainAsPublished = userSettings.mqttSubscribeRequestRetainAsPublished,

            willTopic = userSettings.mqttWillTopic,
            willPayload = userSettings.mqttWillPayload,
            willQos = userSettings.mqttWillQos,
            willRetain = userSettings.mqttWillRetain,
            willMessageExpiryInterval = userSettings.mqttWillMessageExpiryInterval,
            willDelayInterval = userSettings.mqttWillDelayInterval,

            restrictionsReceiveMaximum = userSettings.mqttRestrictionsReceiveMaximum,
            restrictionsSendMaximum = userSettings.mqttRestrictionsSendMaximum,
            restrictionsMaximumPacketSize = userSettings.mqttRestrictionsMaximumPacketSize,
            restrictionsSendMaximumPacketSize = userSettings.mqttRestrictionsSendMaximumPacketSize,
            restrictionsTopicAliasMaximum = userSettings.mqttRestrictionsTopicAliasMaximum,
            restrictionsSendTopicAliasMaximum = userSettings.mqttRestrictionsSendTopicAliasMaximum,
            restrictionsRequestProblemInformation = userSettings.mqttRestrictionsRequestProblemInformation,
            restrictionsRequestResponseInformation = userSettings.mqttRestrictionsRequestResponseInformation
        )
        if (rebuildClient) {
            client = buildClient()
        }
    }

    private fun buildClient(): Mqtt5AsyncClient {
        var builder = MqttClient.builder()
            .useMqttVersion5()
            .serverHost(config.serverHost)
            .serverPort(config.serverPort)

        if (config.useWebSocket) {
            builder = builder
                .webSocketConfig(
                    MqttWebSocketConfig.builder()
                        .subprotocol("mqtt")
                        .serverPath(
                            config.webSocketServerPath.trim().let { path ->
                                if (path.startsWith("/")) path else "/$path"
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

        builder = if (config.username.isNotEmpty() && config.password.isNotEmpty()) {
            builder.simpleAuth()
                .username(config.username)
                .password(UTF_8.encode(config.password))
                .applySimpleAuth()
        } else if (config.username.isNotEmpty()) {
            builder.simpleAuth()
                .username(config.username)
                .applySimpleAuth()
        } else if (config.password.isNotEmpty()) {
            builder.simpleAuth()
                .password(UTF_8.encode(config.password))
                .applySimpleAuth()
        } else {
            builder
        }

        builder = builder.willPublish()
            .topic(mqttVariableReplacement(config.willTopic))
            .qos(config.willQos.toMqttQos())
            .retain(config.willRetain)
            .messageExpiryInterval(config.willMessageExpiryInterval.toLong())
            .delayInterval(config.willDelayInterval.toLong())
            .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
            .contentType("text/plain")
            .payload(mqttVariableReplacement(config.willPayload).toByteArray())
            .userProperties()
                .add("username", config.username)
                .add("appInstanceId", config.appInstanceId)
                .applyUserProperties()
            .applyWillPublish()

        return builder
            .addConnectedListener { connectedListener ->
                addDebugLog(
                    "connect success",
                    "Client ID: ${connectedListener.clientConfig.clientIdentifier.getOrNull()}"
                )
                subscribeToTopics()
            }
            .addDisconnectedListener { context ->
                if (pendingCancelConnect.get()) {
                    context.reconnector.reconnect(false)
                    pendingCancelConnect.set(false)
                    return@addDisconnectedListener
                }
                if (config.enabled && config.automaticReconnect) {
                    context.reconnector
                        .reconnect(context.source != MqttDisconnectSource.USER)
                        .delay(
                            Constants.MQTT_AUTO_RECONNECT_INTERVAL_SECONDS.toLong(),
                            TimeUnit.SECONDS
                        )
                }
                addDebugLog(
                    "disconnected",
                    "source: ${context.source}\nreconnect: ${context.reconnector.isReconnect}\ncause: ${context.cause}"
                )
            }
            .transportConfig()
            .mqttConnectTimeout(config.mqttConnectTimeout.toLong(), TimeUnit.SECONDS)
            .socketConnectTimeout(config.socketConnectTimeout.toLong(), TimeUnit.SECONDS)
            .applyTransportConfig()
            .buildAsync()
    }

    fun connect(
        context: Context,
        onConnected: (() -> Unit)? = null,
        onError: ((String?) -> Unit)? = null
    ) {
        val userSettings = UserSettings(context)
        val systemSettings = SystemSettings(context)
        updateConfig(systemSettings, userSettings)

        if (!config.enabled) {
            onError?.invoke("MQTT is not enabled in app settings.")
            addDebugLog("connect failed", "MQTT is not enabled in settings")
            return
        }
        val c = client
        if (c == null) {
            onError?.invoke("MQTT client is not initialised.")
            addDebugLog("connect failed", "client is not initialised")
            return
        }

        val websocketInfo = if (config.useWebSocket) {
            "yes (path: ${config.webSocketServerPath})"
        } else {
            "no"
        }
        addDebugLog(
            "connect pending...",
            """
                host: ${config.serverHost}
                port: ${config.serverPort}
                tls: ${if (config.useTls) "yes" else "no"}
                websocket: $websocketInfo
                username: ${config.username}
            """.trimIndent()
        )

        var connection = c.connectWith()
            .cleanStart(config.cleanStart)
            .keepAlive(config.keepAlive)
            .userProperties()
                .add("username", config.username)
                .add("appInstanceId", config.appInstanceId)
                .applyUserProperties()

        var rb = connection.restrictions()
            .requestProblemInformation(config.restrictionsRequestProblemInformation)
            .requestResponseInformation(config.restrictionsRequestResponseInformation)

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
        connection
            .send()
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    onConnected?.invoke()
                    publishEventMessage(
                        MqttConnectedEvent(
                            messageId = UUID.randomUUID().toString(),
                            username = config.username,
                            appInstanceId = config.appInstanceId,
                            data = getStatus(context),
                        )
                    )
                } else {
                    addDebugLog("connect failed", throwable.message)
                    throwable.printStackTrace()
                    onError?.invoke(throwable.message)
                }
            }
    }

    fun publishUrlChangedEvent(url: String) {
        val event = MqttUrlChangedEvent(
            messageId = UUID.randomUUID().toString(),
            username = config.username,
            appInstanceId = config.appInstanceId,
            data = MqttUrlChangedEvent.UrlData(url),
        )
        publishEventMessage(event)
    }

    fun publishLockEvent(lockStateType: LockStateType) {
        val event = MqttLockEvent(
            messageId = UUID.randomUUID().toString(),
            username = config.username,
            appInstanceId = config.appInstanceId,
            data = MqttLockEvent.LockStateData(lockStateType)
        )
        publishEventMessage(event)
    }

    fun publishUnlockEvent() {
        val event = MqttUnlockEvent(
            messageId = UUID.randomUUID().toString(),
            username = config.username,
            appInstanceId = config.appInstanceId,
        )
        publishEventMessage(event)
    }

    private fun publishEventMessage(
        event: MqttEventMessage,
        whenComplete: ((Mqtt5PublishResult?, Throwable?) -> Unit)? = null
    ) {
        val payload = MqttEventJsonParser.encodeToString(event)
        val topic = mqttVariableReplacement(
            config.publishEventTopic,
            mapOf(
                MqttVariableNameOption.EVENT_TYPE.name to event.getEventType()
            )
        )
        publishToMqtt(
            topic,
            payload,
            config.publishEventQos,
            config.publishEventRetain,
            messageId = event.messageId,
            whenComplete = whenComplete,
        )
    }

    fun publishStatusResponse(statusRequest: MqttStatusRequest, status: WebviewKioskStatus) {
        val statusMessage = MqttStatusResponse(
            messageId = UUID.randomUUID().toString(),
            username = config.username,
            appInstanceId = config.appInstanceId,
            requestMessageId = statusRequest.messageId,
            data = status,
        )
        publishResponseMessage(
            statusMessage,
            statusRequest,
        )
    }

    fun publishSettingsResponse(settingsRequest: MqttSettingsRequest, settings: JSONObject) {
        val settingsMessage = MqttSettingsResponse(
            messageId = UUID.randomUUID().toString(),
            username = config.username,
            appInstanceId = config.appInstanceId,
            requestMessageId = settingsRequest.messageId,
            data = filterSettingsJson(settings, settingsRequest.data),
        )
        publishResponseMessage(
            settingsMessage,
            settingsRequest
        )
    }

    fun publishSystemInfoResponse(
        systemInfoRequest: MqttSystemInfoRequest,
        systemInfo: SystemInfo
    ) {
        val statusMessage = MqttSystemInfoResponse(
            messageId = UUID.randomUUID().toString(),
            username = config.username,
            appInstanceId = config.appInstanceId,
            requestMessageId = systemInfoRequest.messageId,
            data = systemInfo,
        )
        publishResponseMessage(
            statusMessage,
            systemInfoRequest,
        )
    }

    fun publishErrorResponse(
        errorRequest: MqttErrorRequest,
    ) {
        val errorMessage = MqttErrorResponse(
            messageId = UUID.randomUUID().toString(),
            username = config.username,
            appInstanceId = config.appInstanceId,
            requestMessageId = errorRequest.messageId,
            errorMessage = errorRequest.error,
        )
        publishResponseMessage(
            errorMessage,
            errorRequest,
        )
    }

    private fun publishResponseMessage(
        responseMessage: MqttResponseMessage,
        requestMessage: MqttRequestMessage,
    ) {
        val topic = requestMessage.responseTopic.takeIf { !it.isNullOrEmpty() }
            ?: mqttVariableReplacement(
                config.publishResponseTopic,
                mapOf(
                    MqttVariableNameOption.RESPONSE_TYPE.name to responseMessage.getType()
                )
            )

        val payload = MqttResponseJsonParser.encodeToString(responseMessage)
        publishToMqtt(
            topic,
            payload,
            config.publishResponseQos,
            config.publishResponseRetain,
            correlationData = requestMessage.correlationData?.toByteArray(),
            messageId = responseMessage.messageId
        )
    }

    private fun publishToMqtt(
        topic: String,
        payload: String,
        qos: MqttQosOption,
        retain: Boolean,
        correlationData: ByteArray? = null,
        messageId: String? = null,
        whenComplete: ((Mqtt5PublishResult?, Throwable?) -> Unit)? = null
    ) {
        val c = client ?: return
        if (!config.enabled || !c.state.isConnected) return
        if (!isValidMqttPublishTopic(topic)) {
            addDebugLog(
                "publish failed",
                "topic: $topic\nerror: Invalid publish topic name.",
                messageId,
            )
            return
        }

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
                    whenComplete?.invoke(result, throwable)
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
        }
    }

    private fun subscribeToTopics() {
        subscribeTopic(
            topic = config.subscribeCommandTopic,
            qos = config.subscribeCommandQos,
            retainHandling = config.subscribeCommandRetainHandling,
            retainAsPublished = config.subscribeCommandRetainAsPublished,
            onMessage = { publish, payloadStr ->
                try {
                    val command = MqttCommandJsonParser.decodeFromString<MqttCommandMessage>(payloadStr)
                    addDebugLog(
                        "command received",
                        "topic: ${publish.topic}\ncommand: $command",
                        command.messageId
                    )

                    val targetInstances = command.targetInstances
                    val targetUsernames = command.targetUsernames
                    if (
                        (targetInstances.isNullOrEmpty() || targetInstances.contains(config.appInstanceId))
                        && (targetUsernames.isNullOrEmpty() || targetUsernames.contains(config.username))
                    ) {
                        addDebugLog(
                            "command received",
                            "topic: ${publish.topic}\ncommand: $command",
                            command.messageId
                        )
                        scope.launch { _commands.emit(command) }
                    } else {
                        addDebugLog(
                            "command received (ignored)",
                            "topic: ${publish.topic}\ncommand: $command",
                            command.messageId
                        )
                    }
                } catch (e: Exception) {
                    scope.launch { _commands.emit(MqttErrorCommand(e.message ?: e.toString())) }
                    val messageId = getValueFromPrimitiveJson(payloadStr, "messageId")
                    addDebugLog("command error", e.message, messageId)
                }
            }
        )

        subscribeTopic(
            topic = config.subscribeSettingsTopic,
            qos = config.subscribeSettingsQos,
            retainHandling = config.subscribeSettingsRetainHandling,
            retainAsPublished = config.subscribeSettingsRetainAsPublished,
            onMessage = { publish, payloadStr ->
                val json = Json.parseToJsonElement(payloadStr).jsonObject

                val settingsMessage = MqttSettingsMessage(
                    messageId = getValueFromPrimitiveJson(payloadStr, "messageId"),
                    refresh = json["refresh"]?.jsonPrimitive?.booleanOrNull ?: true,
                    showToast = json["showToast"]?.jsonPrimitive?.booleanOrNull ?: true,
                    targetInstances = runCatching {
                        json["targetInstances"]?.jsonArray?.mapNotNull {
                            it.jsonPrimitive.contentOrNull
                        }?.toSet()
                    }.getOrNull(),
                    targetUsernames = runCatching {
                        json["targetUsernames"]?.jsonArray?.mapNotNull {
                            it.jsonPrimitive.contentOrNull
                        }?.toSet()
                    }.getOrNull(),
                    data = json["data"]?.toString() ?: "{}"
                )

                val targetInstances = settingsMessage.targetInstances
                val targetUsernames = settingsMessage.targetUsernames
                if (
                    (targetInstances.isNullOrEmpty() || targetInstances.contains(config.appInstanceId))
                    && (targetUsernames.isNullOrEmpty() || targetUsernames.contains(config.username))
                ) {
                    addDebugLog(
                        "settings received",
                        "topic: ${publish.topic}\nsettings: ${settingsMessage.data}",
                        messageId = settingsMessage.messageId,
                    )
                    scope.launch { _settings.emit(settingsMessage) }
                } else {
                    addDebugLog(
                        "settings received (ignored)",
                        "topic: ${publish.topic}\nsettings: ${settingsMessage.data}",
                        messageId = settingsMessage.messageId,
                    )
                }
            }
        )

        subscribeTopic(
            topic = config.subscribeRequestTopic,
            qos = config.subscribeRequestQos,
            retainHandling = config.subscribeRequestRetainHandling,
            retainAsPublished = config.subscribeRequestRetainAsPublished,
            onMessage = { publish, payloadStr ->
                try {
                    val request = MqttRequestJsonParser.decodeFromString<MqttRequestMessage>(payloadStr)

                    @SuppressLint("NewApi")
                    if (publish.responseTopic.isPresent) {
                        request.responseTopic = publish.responseTopic.get().toString()
                    }
                    @SuppressLint("NewApi")
                    if (publish.correlationData.isPresent) {
                        val buf = publish.correlationData.get()
                        val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                        request.correlationData = String(bytes, UTF_8)
                    }
                    val targetInstances = request.targetInstances
                    val targetUsernames = request.targetUsernames
                    if (
                        (targetInstances.isNullOrEmpty() || targetInstances.contains(config.appInstanceId))
                        && (targetUsernames.isNullOrEmpty() || targetUsernames.contains(config.username))
                    ) {
                        addDebugLog(
                            "request received",
                            "topic: ${publish.topic}\nrequest: $request",
                            request.messageId
                        )
                        scope.launch { _requests.emit(request) }
                    } else {
                        addDebugLog(
                            "request received (ignored)",
                            "topic: ${publish.topic}\nrequest: $request",
                            request.messageId
                        )
                    }
                } catch (e: Exception) {
                    val messageId = getValueFromPrimitiveJson(payloadStr, "messageId")
                    scope.launch {
                        _requests.emit(
                            MqttErrorRequest(
                                messageId = messageId,
                                responseTopic = getValueFromPrimitiveJson(payloadStr, "responseTopic"),
                                correlationData = getValueFromPrimitiveJson(payloadStr, "correlationData"),
                                targetInstances = runCatching {
                                    Json.parseToJsonElement(payloadStr)
                                        .jsonObject["targetInstances"]
                                        ?.jsonArray
                                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                        ?.toSet()
                                    }.getOrNull(),
                                error = e.message ?: e.toString(),
                            )
                        )
                    }
                    addDebugLog("request error", e.message, messageId)
                }
            }
        )
    }

    private fun getValueFromPrimitiveJson(payloadStr: String, key: String): String? {
        return runCatching {
            Json.parseToJsonElement(payloadStr)
                .jsonObject[key]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun subscribeTopic(
        topic: String,
        qos: MqttQosOption,
        retainHandling: MqttRetainHandlingOption,
        retainAsPublished: Boolean,
        onMessage: (publish: Mqtt5Publish, payloadStr: String) -> Unit
    ) {
        val c = client ?: return
        val subscribeTopic = mqttVariableReplacement(topic)
        if (!isValidMqttSubscribeTopic(subscribeTopic)) {
            addDebugLog("subscribe failed", "topic: $subscribeTopic\nerror: Invalid topic name")
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
                    val payloadStr = publish.payloadAsBytes.toString(UTF_8)
                    onMessage(publish, payloadStr)
                }
                .send()
                .whenComplete { _, throwable ->
                    if (throwable == null) {
                        addDebugLog("subscribe success", "topic: $subscribeTopic")
                    } else {
                        addDebugLog("subscribe error", "topic: $subscribeTopic\nerror: $throwable")
                    }
                }
        } catch (e: Exception) {
            addDebugLog("subscribe failed", "topic: $subscribeTopic\nerror: $e")
            e.printStackTrace()
        }
    }

    fun isConnectedOrReconnect(): Boolean = client?.state?.isConnectedOrReconnect ?: false

    fun getState() = client?.state ?: MqttClientState.DISCONNECTED

    fun cancelConnect(): Boolean {
        if (pendingCancelConnect.get()) {
            return false
        }
        pendingCancelConnect.set(true)
        addDebugLog("connect cancel requested", "User manually triggered cancellation request.")
        return true
    }

    fun disconnect(
        cause: MqttDisconnectingEvent.DisconnectCause,
        onDisconnected: (() -> Unit)? = null,
        onError: ((String?) -> Unit)? = null,
    ) {
        val c = client
        if (c == null) {
            addDebugLog("disconnect - not initialised")
            onDisconnected?.invoke()
            return
        }

        if (!c.state.isConnected) {
            addDebugLog("disconnect - not connected", "state: ${c.state}")
            onDisconnected?.invoke()
            return
        }

        publishEventMessage(
            MqttDisconnectingEvent(
                messageId = UUID.randomUUID().toString(),
                username = config.username,
                appInstanceId = config.appInstanceId,
                data = MqttDisconnectingEvent.DisconnectingData(
                    cause = cause,
                )
            ),
            whenComplete = { _, _ ->
                @SuppressLint("NewApi")
                c.disconnectWith()
                    .userProperties()
                        .add("username", config.username)
                        .add("appInstanceId", config.appInstanceId)
                        .applyUserProperties()
                    .send()
                    .whenComplete { _, throwable ->
                    if (throwable == null) {
                        onDisconnected?.invoke()
                    } else {
                        addDebugLog("disconnect failed", throwable.message)
                        onError?.invoke(throwable.message)
                    }
                }
            }
        )
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
        val variableReplacementMap = mapOf(
            MqttVariableNameOption.APP_INSTANCE_ID.name to config.appInstanceId,
            MqttVariableNameOption.USERNAME.name to config.username,
        ) + additionalReplacementMap
        val regex = "\\$\\{([^}]+)\\}".toRegex()
        return regex.replace(value) { matchResult ->
            val key = matchResult.groupValues[1]
            variableReplacementMap[key] ?: matchResult.value
        }.trim()
    }
}
