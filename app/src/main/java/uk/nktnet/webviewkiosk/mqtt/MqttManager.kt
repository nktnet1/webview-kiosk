package uk.nktnet.webviewkiosk.mqtt

import android.annotation.SuppressLint
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.config.option.MqttRetainHandlingOption
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

    private val _settings = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val settings: SharedFlow<String> get() = _settings

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

    private fun updateConfig(systemSettings: SystemSettings, userSettings: UserSettings) {
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

            subscribeCommandTopic = userSettings.mqttSubscribeCommandTopic,
            subscribeCommandQos = userSettings.mqttSubscribeCommandQos,
            subscribeCommandRetainHandling = userSettings.mqttSubscribeCommandRetainHandling,
            subscribeCommandRetainAsPublished = userSettings.mqttSubscribeCommandRetainAsPublished,

            subscribeSettingsTopic = userSettings.mqttSubscribeSettingsTopic,
            subscribeSettingsQos = userSettings.mqttSubscribeSettingsQos,
            subscribeSettingsRetainHandling = userSettings.mqttSubscribeSettingsRetainHandling,
            subscribeSettingsRetainAsPublished = userSettings.mqttSubscribeSettingsRetainAsPublished,

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
        client = buildClient()
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
                if (config.automaticReconnect) {
                    context.reconnector
                        .reconnect(context.source != MqttDisconnectSource.USER)
                        .delay(3, TimeUnit.SECONDS)
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

    @SuppressLint("NewApi")
    fun connect(
        systemSettings: SystemSettings,
        userSettings: UserSettings,
        onConnected: (() -> Unit)? = null,
        onError: ((String?) -> Unit)? = null
    ) {
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
        c.connectWith()
            .cleanStart(config.cleanStart)
            .keepAlive(config.keepAlive)
            .simpleAuth()
                .username(config.username)
                .password(UTF_8.encode(config.password))
                .applySimpleAuth()
            .willPublish()
                .topic(config.willTopic)
                .qos(config.willQos.toMqttQos())
                .retain(config.willRetain)
                .messageExpiryInterval(config.willMessageExpiryInterval.toLong())
                .delayInterval(config.willDelayInterval.toLong())
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .contentType("text/plain")
                .payload(config.willPayload.toByteArray())
                .applyWillPublish()
            .restrictions()
                .receiveMaximum(config.restrictionsReceiveMaximum)
                .sendMaximum(config.restrictionsSendMaximum)
                .maximumPacketSize(config.restrictionsMaximumPacketSize)
                .sendMaximumPacketSize(config.restrictionsSendMaximumPacketSize)
                .topicAliasMaximum(config.restrictionsTopicAliasMaximum)
                .sendTopicAliasMaximum(config.restrictionsSendTopicAliasMaximum)
                .requestProblemInformation(config.restrictionsRequestProblemInformation)
                .requestResponseInformation(config.restrictionsRequestResponseInformation)
                .applyRestrictions()
            .send()
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    onConnected?.invoke()
                } else {
                    addDebugLog("connect failed", "${throwable.message}.")
                    throwable.printStackTrace()
                    onError?.invoke(throwable.message)
                }
            }
    }

    fun publishUrlVisitedEvent(url: String) {
        val event = MqttUrlVisitedEvent(
            identifier = UUID.randomUUID().toString(),
            url = url,
            appInstanceId = config.appInstanceId
        )
        val payload = Json.encodeToString(event)
        publishEvent(event.event, payload)
    }

    fun publishLockEvent() {
        val event = MqttLockEvent(
            identifier = UUID.randomUUID().toString(),
            appInstanceId = config.appInstanceId
        )
        val payload = Json.encodeToString(event)
        publishEvent(event.event, payload)
    }

    fun publishUnlockEvent() {
        val event = MqttUnlockEvent(
            identifier = UUID.randomUUID().toString(),
            appInstanceId = config.appInstanceId
        )
        val payload = Json.encodeToString(event)
        publishEvent(event.event, payload)
    }

    private fun publishEvent(event: String, payload: String) {
        val c = client ?: return
        if (!config.enabled || !c.state.isConnected) {
            return
        }
        val topic = mqttVariableReplacement(
            config.publishEventTopic,
            mapOf(
                "EVENT_NAME" to event,
            )
        )
        try {
            c.publishWith()
                .topic(topic)
                .qos(config.publishEventQos.toMqttQos())
                .retain(config.publishEventRetain)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .contentType("application/json")
                .payload(payload.toByteArray())
                .send()
            addDebugLog("publish success", "topic: $topic\npayload: $payload")
        } catch (e: Exception) {
            addDebugLog("publish failed", "topic: $topic\nevent: $event\nerror: $e")
        }
    }

    private fun subscribeToTopics() {
        val subscribeTopic = mqttVariableReplacement(config.subscribeCommandTopic)
        subscribeTopic(
            topic = subscribeTopic,
            qos = config.subscribeCommandQos,
            retainHandling = config.subscribeCommandRetainHandling,
            retainAsPublished = config.subscribeCommandRetainAsPublished,
            onMessage = { publishTopic, payloadStr ->
                try {
                    val command = MqttCommandJsonParser.decodeFromString<MqttCommandMessage>(payloadStr)
                    scope.launch { _commands.emit(command) }
                    addDebugLog(
                        "command received",
                        "topic: $publishTopic\ncommand: $command",
                        command.identifier
                    )
                } catch (e: Exception) {
                    scope.launch { _commands.emit(MqttMqttCommandError(e.message ?: e.toString())) }
                    val identifier = getIdentifier(payloadStr)
                    addDebugLog("command error", e.message, identifier)
                }
            }
        )

        subscribeTopic(
            topic = config.subscribeSettingsTopic,
            qos = config.subscribeSettingsQos,
            retainHandling = config.subscribeSettingsRetainHandling,
            retainAsPublished = config.subscribeSettingsRetainAsPublished,
            onMessage = { publishTopic, payloadStr ->
                addDebugLog(
                    "settings received",
                    "topic: $publishTopic\npayload: $payloadStr",
                    identifier = getIdentifier(payloadStr)
                )
                scope.launch { _settings.emit(payloadStr) }
            }
        )
    }

    private fun getIdentifier(payloadStr: String): String? {
        return runCatching {
            Json.parseToJsonElement(payloadStr)
                .jsonObject["identifier"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    @SuppressLint("NewApi")
    private fun subscribeTopic(
        topic: String,
        qos: MqttQosOption,
        retainHandling: MqttRetainHandlingOption,
        retainAsPublished: Boolean,
        onMessage: (publishTopic: String, payloadStr: String) -> Unit
    ) {
        val c = client ?: return
        try {
            c.subscribeWith()
                .topicFilter(mqttVariableReplacement(topic))
                .qos(qos.toMqttQos())
                .retainHandling(retainHandling.toMqttRetainHandling())
                .retainAsPublished(retainAsPublished)
                .noLocal(true)
                .callback { publish ->
                    val payloadStr = publish.payloadAsBytes.toString(UTF_8)
                    onMessage(publish.topic.toString(), payloadStr)
                }
                .send()
                .whenComplete { _, throwable ->
                    if (throwable == null) {
                        addDebugLog("subscribe success", "topic: $topic")
                    } else {
                        addDebugLog("subscribe error", "topic: $topic\nerror: $throwable")
                    }
                }
        } catch (e: Exception) {
            addDebugLog("subscribe failed", e.message)
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

    @SuppressLint("NewApi")
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
            "APP_INSTANCE_ID" to config.appInstanceId
        ) + additionalReplacementMap
        val regex = "\\$\\{([^}]+)\\}".toRegex()
        return regex.replace(value) { matchResult ->
            val key = matchResult.groupValues[1]
            variableReplacementMap[key] ?: matchResult.value
        }.trim()
    }
}
