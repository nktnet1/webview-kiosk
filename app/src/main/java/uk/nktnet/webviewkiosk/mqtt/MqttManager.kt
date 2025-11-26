package uk.nktnet.webviewkiosk.mqtt

import uk.nktnet.webviewkiosk.mqtt.messages.MqttSettingsMessage
import android.annotation.SuppressLint
import android.content.Context
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.config.option.MqttRetainHandlingOption
import uk.nktnet.webviewkiosk.config.option.MqttVariableNameOption
import uk.nktnet.webviewkiosk.mqtt.messages.MqttCommandJsonParser
import uk.nktnet.webviewkiosk.mqtt.messages.MqttCommandMessage
import uk.nktnet.webviewkiosk.mqtt.messages.MqttConnectedEvent
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
import uk.nktnet.webviewkiosk.mqtt.messages.MqttUrlVisitedEvent
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
import kotlin.text.Charsets.UTF_8

data class MqttLogEntry(
    val timestamp: Date,
    val tag: String,
    val message: String?,
    val identifier: String?,
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

    private fun addDebugLog(tag: String, message: String? = null, identifier: String? = null) {
        val logEntry = MqttLogEntry(Date(), tag, message, identifier)
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

        if (config.clientId.isNotEmpty()) {
            builder = builder.identifier(mqttVariableReplacement(config.clientId))
        }

        if (config.useTls) {
            builder = builder.sslWithDefaultConfig()
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
            .applyWillPublish()

        return builder
            .addConnectedListener {
                addDebugLog("connect success")
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
        addDebugLog("connect pending", "Attempting to connect...")

        var connection = c.connectWith()
            .cleanStart(config.cleanStart)
            .keepAlive(config.keepAlive)

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
                    publishEventTopic(
                        MqttConnectedEvent(
                            identifier = UUID.randomUUID().toString(),
                            appInstanceId = config.appInstanceId,
                            data = getStatus(context)
                        )
                    )
                } else {
                    addDebugLog("connect failed", throwable.message)
                    throwable.printStackTrace()
                    onError?.invoke(throwable.message)
                }
            }
    }

    fun publishUrlVisitedEvent(url: String) {
        val event = MqttUrlVisitedEvent(
            identifier = UUID.randomUUID().toString(),
            appInstanceId = config.appInstanceId,
            data = MqttUrlVisitedEvent.UrlData(url),
        )
        publishEventTopic(event)
    }

    fun publishLockEvent() {
        val event = MqttLockEvent(
            identifier = UUID.randomUUID().toString(),
            appInstanceId = config.appInstanceId
        )
        publishEventTopic(event)
    }

    fun publishUnlockEvent() {
        val event = MqttUnlockEvent(
            identifier = UUID.randomUUID().toString(),
            appInstanceId = config.appInstanceId
        )
        publishEventTopic(event)
    }

    private fun publishEventTopic(event: MqttEventMessage) {
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
            identifier = event.identifier,
        )
    }

    fun publishStatusResponse(statusRequest: MqttStatusRequest, status: WebviewKioskStatus) {
        val statusMessage = MqttStatusResponse(
            identifier = statusRequest.identifier,
            appInstanceId = config.appInstanceId,
            data = status
        )
        publishResponseMessage(
            statusMessage,
            statusRequest,
        )
    }

    fun publishSettingsResponse(settingsRequest: MqttSettingsRequest, settings: JSONObject) {
        val settingsMessage = MqttSettingsResponse(
            identifier = settingsRequest.identifier,
            appInstanceId = config.appInstanceId,
            data = filterSettingsJson(settings, settingsRequest.settings)
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
            identifier = systemInfoRequest.identifier,
            appInstanceId = config.appInstanceId,
            data = systemInfo
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
            identifier = errorRequest.identifier,
            appInstanceId = config.appInstanceId,
            errorMessage = errorRequest.error
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

        responseMessage.identifier = responseMessage.identifier?.let {
            "res:$it"
        } ?: UUID.randomUUID().toString()
        val payload = MqttResponseJsonParser.encodeToString(responseMessage)
        publishToMqtt(
            topic,
            payload,
            config.publishResponseQos,
            config.publishResponseRetain,
            correlationData = requestMessage.correlationData?.toByteArray(),
            identifier = responseMessage.identifier
        )
    }

    private fun publishToMqtt(
        topic: String,
        payload: String,
        qos: MqttQosOption,
        retain: Boolean,
        correlationData: ByteArray? = null,
        identifier: String? = null,
    ) {
        val c = client ?: return
        if (!config.enabled || !c.state.isConnected) return
        if (!isValidMqttPublishTopic(topic)) {
            addDebugLog(
                "publish failed",
                "topic: $topic\nerror: Invalid publish topic name.",
                identifier,
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
                .send()
                .whenComplete { _, throwable ->
                    if (throwable == null) {
                        addDebugLog(
                            "publish success",
                            "topic: $topic\npayload: $payload",
                            identifier,
                        )
                    } else {
                        addDebugLog(
                            "publish error",
                            "topic: $topic\nerror: $throwable",
                            identifier,
                        )
                    }
                }
        } catch (e: Exception) {
            addDebugLog(
                "publish failed",
                "topic: $topic\nerror: $e",
                identifier,
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
                        command.identifier
                    )
                    scope.launch { _commands.emit(command) }
                } catch (e: Exception) {
                    scope.launch { _commands.emit(MqttErrorCommand(e.message ?: e.toString())) }
                    val identifier = getValueFromPrimitiveJson(payloadStr, "identifier")
                    addDebugLog("command error", e.message, identifier)
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
                val identifier = getValueFromPrimitiveJson(payloadStr, "identifier")

                val applyNow = json["applyNow"]?.jsonPrimitive?.booleanOrNull ?: true
                val showToast = json["showToast"]?.jsonPrimitive?.booleanOrNull ?: true
                val settingsStr = json["settings"]?.toString() ?: "{}"

                val settingsMessage = MqttSettingsMessage(
                    identifier = identifier,
                    refresh = applyNow,
                    showToast = showToast,
                    settings = settingsStr
                )
                addDebugLog(
                    "settings received",
                    "topic: ${publish.topic}\nsettings: $settingsStr",
                    identifier = identifier,
                )
                scope.launch { _settings.emit(settingsMessage) }
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
                    addDebugLog(
                        "request received",
                        "topic: ${publish.topic}\nrequest: $request",
                        request.identifier
                    )
                    scope.launch { _requests.emit(request) }
                } catch (e: Exception) {
                    val identifier = getValueFromPrimitiveJson(payloadStr, "identifier")
                    scope.launch {
                        _requests.emit(
                            MqttErrorRequest(
                                identifier = identifier,
                                responseTopic = getValueFromPrimitiveJson(payloadStr, "responseTopic"),
                                correlationData = getValueFromPrimitiveJson(payloadStr, "correlationData"),
                                error = e.message ?: e.toString()
                            )
                        )
                    }
                    addDebugLog("request error", e.message, identifier)
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
        onDisconnected: (() -> Unit)? = null,
        onError: ((String?) -> Unit)? = null
    ) {
        val c = client
        if (c == null) {
            addDebugLog("disconnect - not initialised")
            onDisconnected?.invoke()
            return
        }
        @SuppressLint("NewApi")
        c.disconnect().whenComplete { _, throwable ->
            if (throwable == null) {
                onDisconnected?.invoke()
            } else {
                addDebugLog("disconnect failed", throwable.message)
                onError?.invoke(throwable.message)
            }
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
