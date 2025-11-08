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
    private val _commands = MutableSharedFlow<CommandMessage>(extraBufferCapacity = 100)
    val commands: SharedFlow<CommandMessage> get() = _commands

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
            enabled = userSettings.mqttEnabled,
            clientId = mqttVariableReplacement(systemSettings, userSettings.mqttClientId),
            serverHost = userSettings.mqttServerHost,
            serverPort = userSettings.mqttServerPort,
            username = userSettings.mqttUsername,
            password = userSettings.mqttPassword,
            useTls = userSettings.mqttUseTls,
            automaticReconnect = userSettings.mqttAutomaticReconnect,
            cleanStart = userSettings.mqttCleanStart,
            keepAlive = userSettings.mqttKeepAlive,
            connectTimeout = userSettings.mqttConnectTimeout,

            subscribeCommandTopic = mqttVariableReplacement(systemSettings, userSettings.mqttSubscribeCommandTopic),
            subscribeCommandQos = userSettings.mqttSubscribeCommandQos,
            subscribeCommandRetainHandling = userSettings.mqttSubscribeCommandRetainHandling,
            subscribeCommandRetainAsPublished = userSettings.mqttSubscribeCommandRetainAsPublished,

            subscribeSettingsTopic = mqttVariableReplacement(systemSettings, userSettings.mqttSubscribeSettingsTopic),
            subscribeSettingsQos = userSettings.mqttSubscribeSettingsQos,
            subscribeSettingsRetainHandling = userSettings.mqttSubscribeSettingsRetainHandling,
            subscribeSettingsRetainAsPublished = userSettings.mqttSubscribeSettingsRetainAsPublished,

            willTopic = userSettings.mqttWillTopic,
            willPayload = userSettings.mqttWillPayload,
            willQos = userSettings.mqttWillQos,
            willRetain = userSettings.mqttWillRetain,
            willMessageExpiryInterval = userSettings.mqttWillMessageExpiryInterval,
            willDelayInterval = userSettings.mqttWillDelayInterval
        )
        client = buildClient()
    }

    private fun buildClient(): Mqtt5AsyncClient {
        var builder = MqttClient.builder()
            .useMqttVersion5()
            .serverHost(config.serverHost)
            .serverPort(config.serverPort)

        if (config.clientId.isNotEmpty()) {
            builder = builder.identifier(config.clientId)
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
            .mqttConnectTimeout(config.connectTimeout.toLong(), TimeUnit.SECONDS)
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
                .messageExpiryInterval(config.willMessageExpiryInterval * 1L)
                .delayInterval(config.willDelayInterval * 1L)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .contentType("text/plain")
                .payload(config.willPayload.toByteArray())
                .applyWillPublish()
            .send()
            .whenComplete { conn, throwable ->
                if (throwable == null) {
                    onConnected?.invoke()
                } else {
                    addDebugLog("connect failed", "${throwable.message}.")
                    throwable.printStackTrace()
                    onError?.invoke(throwable.message)
                }
            }
    }

    private fun subscribeToTopics() {
        subscribeTopic(
            topic = config.subscribeCommandTopic,
            qos = config.subscribeCommandQos,
            retainHandling = config.subscribeCommandRetainHandling,
            retainAsPublished = config.subscribeCommandRetainAsPublished,
            onMessage = { publishTopic, payloadStr ->
                try {
                    val command = MqttCommandJsonParser.decodeFromString<CommandMessage>(payloadStr)
                    scope.launch { _commands.emit(command) }
                    addDebugLog(
                        "command received",
                        "topic: $publishTopic\ncommand: $command",
                        command.identifier
                    )
                } catch (e: Exception) {
                    scope.launch { _commands.emit(MqttCommandError(e.message ?: e.toString())) }
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
                .topicFilter(topic)
                .qos(qos.toMqttQos())
                .retainHandling(retainHandling.toMqttRetainHandling())
                .retainAsPublished(retainAsPublished)
                .noLocal(true)
                .callback { publish ->
                    val payloadStr = publish.payloadAsBytes.toString(UTF_8)
                    onMessage(publish.topic.toString(), payloadStr)
                }
                .send()
        } catch (e: Exception) {
            addDebugLog("subscribe ($topic) failed", e.message)
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

    fun mqttVariableReplacement(systemSettings: SystemSettings, input: String): String {
        val variableMap = mapOf(
            "APP_INSTANCE_ID" to systemSettings.appInstanceId,
        )
        val regex = "\\$\\{([^}]+)\\}".toRegex()
        return regex.replace(input) { matchResult ->
            val key = matchResult.groupValues[1]
            variableMap[key] ?: matchResult.value
        }
    }
}
