package uk.nktnet.webviewkiosk.mqtt

import android.annotation.SuppressLint
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.nktnet.webviewkiosk.config.UserSettings
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayDeque
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

    private val logHistory = ArrayDeque<MqttLogEntry>(100)
    private val _debugLog = MutableSharedFlow<MqttLogEntry>(extraBufferCapacity = 100)
    val debugLog: SharedFlow<MqttLogEntry> get() = _debugLog

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

    private fun updateConfig(userSettings: UserSettings) {
        config = MqttConfig(
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
            connectTimeout = userSettings.mqttConnectTimeout,
            subscribeCommandTopic = userSettings.mqttSubscribeCommandTopic,
            subscribeCommandQos = userSettings.mqttSubscribeCommandQos,
            subscribeCommandRetainHandling = userSettings.mqttSubscribeCommandRetainHandling,
            subscribeCommandRetainAsPublished = userSettings.mqttSubscribeCommandRetainAsPublished,
            subscribeSettingsTopic = userSettings.mqttSubscribeSettingsTopic,
            subscribeSettingsQos = userSettings.mqttSubscribeSettingsQos,
            subscribeSettingsRetainHandling = userSettings.mqttSubscribeSettingsRetainHandling,
            subscribeSettingsRetainAsPublished = userSettings.mqttSubscribeSettingsRetainAsPublished
        )
        client = buildClient()
    }

    private fun buildClient(): Mqtt5AsyncClient {
        var builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier(config.clientId)
            .serverHost(config.serverHost)
            .serverPort(config.serverPort)

        if (config.useTls) {
            builder = builder.sslWithDefaultConfig()
        }
        builder = if (config.automaticReconnect) {
            builder.automaticReconnectWithDefaultConfig()
        } else {
            builder.automaticReconnect(null)
        }

        return builder
            .transportConfig()
            .mqttConnectTimeout(config.connectTimeout.toLong(), TimeUnit.SECONDS)
            .applyTransportConfig()
            .buildAsync()
    }

    @SuppressLint("NewApi")
    fun connect(
        userSettings: UserSettings,
        onConnected: (() -> Unit)? = null,
        onError: ((String?) -> Unit)? = null
    ) {
        updateConfig(userSettings)

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
        c.connectWith()
            .cleanStart(config.cleanStart)
            .keepAlive(config.keepAlive)
            .simpleAuth()
            .username(config.username)
            .password(UTF_8.encode(config.password))
            .applySimpleAuth()
            .send()
            .whenComplete { conn, throwable ->
                if (throwable != null) {
                    addDebugLog("connect failed", "${throwable.message}.")
                    throwable.printStackTrace()
                    onError?.invoke(throwable.message)
                } else {
                    subscribeToTopics()
                    addDebugLog("connect success")
                    onConnected?.invoke()
                }
            }
    }

    private fun subscribeToTopics() {
        subscribeCommandTopic()
    }

    private fun subscribeCommandTopic() {
        val c = client ?: return
        try {
            c.subscribeWith()
                .topicFilter(config.subscribeCommandTopic)
                .qos(config.subscribeCommandQos.toMqttQos())
                .retainHandling(config.subscribeCommandRetainHandling.toMqttRetainHandling())
                .retainAsPublished(config.subscribeCommandRetainAsPublished)
                .noLocal(true)
                .callback { publish ->
                    val payloadStr = publish.payloadAsBytes.toString(UTF_8)
                    try {
                        val command = JsonParser.decodeFromString<CommandMessage>(payloadStr)
                        scope.launch { _commands.emit(command) }
                        addDebugLog(
                            "command received",
                            "topic: ${publish.topic}\ncommand: $command",
                            command.identifier,
                        )
                    } catch (e: Exception) {
                        scope.launch {
                            _commands.emit(MqttCommandError(e.message ?: e.toString()))
                        }
                        val identifier = runCatching {
                            JsonParser.parseToJsonElement(payloadStr)
                                .jsonObject["identifier"]?.jsonPrimitive?.contentOrNull
                        }.getOrNull()
                        addDebugLog("command error", e.message, identifier)
                    }
                }
                .send()
        } catch (e: Exception) {
            addDebugLog(
                "subscribe (command) failed",
                e.message,
            )
            e.printStackTrace()
        }
    }

    fun isConnectedOrReconnect(): Boolean = client?.state?.isConnectedOrReconnect ?: false

    fun getState() = client?.state ?: MqttClientState.DISCONNECTED

    @SuppressLint("NewApi")
    fun disconnect(onDisconnected: (() -> Unit)? = null) {
        val c = client
        if (c == null) {
            onDisconnected?.invoke()
            return
        }
        c.disconnect().whenComplete { _, _ ->
            addDebugLog("disconnected")
            onDisconnected?.invoke()
        }
    }
}
