package uk.nktnet.webviewkiosk.mqtt

import android.annotation.SuppressLint
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.UserSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayDeque
import kotlin.text.Charsets.UTF_8

object MqttManager {
    private var client: Mqtt5AsyncClient? = null
    private lateinit var config: MqttConfig

    private val scope = CoroutineScope(Dispatchers.Default)
    private val _commands = MutableSharedFlow<CommandMessage>(extraBufferCapacity = 100)
    val commands: SharedFlow<CommandMessage> get() = _commands

    private val logHistory = ArrayDeque<String>(100)
    private val _debugLog = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val debugLog: SharedFlow<String> get() = _debugLog

    private fun addDebugLog(tag: String, message: String? = null) {
        val timestamp = SimpleDateFormat("yyyy/MM/dd hh:mm:ss a", Locale.getDefault())
            .format(Date())
        val entry = "$timestamp | $tag${if (!message.isNullOrEmpty()) "\n$message" else ""}"
        synchronized(logHistory) {
            if (logHistory.size >= 100) logHistory.removeFirst()
            logHistory.addLast(entry)
        }
        println("[MQTT] $entry")
        scope.launch { _debugLog.emit(entry) }
    }

    val debugLogHistory: List<String>
        get() = synchronized(logHistory) { logHistory.toList() }

    fun init(userSettings: UserSettings) {
        disconnect {
            config = MqttConfig(
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
            addDebugLog("initialised", "host=${config.serverHost}\nport=${config.serverPort}")
        }
    }

    private fun buildClient(): Mqtt5AsyncClient {
        var builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier(config.clientId)
            .serverHost(config.serverHost)
            .serverPort(config.serverPort)

        if (config.useTls) builder = builder.sslWithDefaultConfig()
        if (config.automaticReconnect) builder = builder.automaticReconnectWithDefaultConfig()

        return builder
            .transportConfig()
            .mqttConnectTimeout(config.connectTimeout.toLong(), TimeUnit.SECONDS)
            .applyTransportConfig()
            .buildAsync()
    }

    @SuppressLint("NewApi")
    fun connect(onConnected: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        val c = client ?: return
        c.connectWith()
            .cleanStart(config.cleanStart)
            .keepAlive(config.keepAlive)
            .simpleAuth()
            .username(config.username)
            .password(UTF_8.encode(config.password))
            .applySimpleAuth()
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    addDebugLog("connect failed", "${throwable.message}.")
                    onError?.invoke(throwable)
                } else {
                    addDebugLog("connected")
                    onConnected?.invoke()
                    subscribeToTopics()
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
                        addDebugLog("command","topic=${publish.topic}\ncommand=$command")
                    } catch (e: Exception) {
                        addDebugLog("command", e.message)
                        e.printStackTrace()
                    }
                }
                .send()
        } catch (e: Exception) {
            addDebugLog("subscribe (command) failed: ${e.message}")
            e.printStackTrace()
        }
    }

    fun isConnectedOrReconnect(): Boolean = client?.state?.isConnectedOrReconnect ?: false

    @SuppressLint("NewApi")
    fun disconnect(onDisconnected: (() -> Unit)? = null) {
        val c = client
        if (c == null || !isConnectedOrReconnect()) {
            onDisconnected?.invoke()
            return
        }

        c.disconnect().whenComplete { _, _ ->
            addDebugLog("disconnected")
            onDisconnected?.invoke()
        }
    }
}
