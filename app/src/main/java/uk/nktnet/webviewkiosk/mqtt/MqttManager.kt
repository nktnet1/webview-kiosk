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
import uk.nktnet.webviewkiosk.config.UserSettings
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets.UTF_8

object MqttManager {
    private var client: Mqtt5AsyncClient? = null
    private lateinit var config: MqttConfig

    private val scope = CoroutineScope(Dispatchers.Default)
    private val _commands = MutableSharedFlow<CommandMessage>(extraBufferCapacity = 100)
    val commands: SharedFlow<CommandMessage> get() = _commands

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
                if (throwable != null) onError?.invoke(throwable)
                else {
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
                    println("[MQTT] received topic: ${publish.topic}")
                    val payloadStr = publish.payloadAsBytes.toString(UTF_8)
                    try {
                        val command = JsonParser.decodeFromString<CommandMessage>(payloadStr)
                        scope.launch { _commands.emit(command) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .send()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("NewApi")
    fun disconnect(onDisconnected: (() -> Unit)? = null) {
        val c = client ?: run {
            onDisconnected?.invoke()
            return
        }
        if (c.state == MqttClientState.CONNECTED) c.disconnect().whenComplete { _, _ -> onDisconnected?.invoke() }
        else onDisconnected?.invoke()
    }

    fun isConnected(): Boolean {
        return client?.state?.isConnected ?: false
    }
}
