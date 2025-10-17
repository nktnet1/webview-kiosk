package uk.nktnet.webviewkiosk.mqtt

import android.annotation.SuppressLint
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client

/**
 * For @SuppressLint("NewApi") regarding API 24 warnings, this is resolved with
 * https://hivemq.github.io/hivemq-mqtt-client/docs/installation/android/#android-api-levels-below-24
 */

data class MqttConfig(
    val brokerUrl: String,
    val port: Int = 1883,
    val clientId: String = "webviewkiosk-client",
    val username: String? = null,
    val password: String? = null,
    val topic: String
)

open class MqttSubscriber(initialConfig: MqttConfig) {

    private var config: MqttConfig = initialConfig
    private var client: Mqtt5AsyncClient = buildClient(config)

    private fun buildClient(cfg: MqttConfig): Mqtt5AsyncClient {
        return Mqtt5Client.builder()
            .identifier(cfg.clientId)
            .serverHost(cfg.brokerUrl)
            .serverPort(cfg.port)
            .apply {
                if (!cfg.username.isNullOrEmpty() && cfg.password != null) {
                    simpleAuth()
                        .username(cfg.username)
                        .password(cfg.password.toByteArray())
                        .applySimpleAuth()
                }
            }
            .buildAsync()
    }

    @SuppressLint("NewApi")
    fun connect(onConnected: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        client.connect()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    onError?.invoke(throwable)
                } else {
                    onConnected?.invoke()
                    subscribeToTopic()
                }
            }
    }

    private fun subscribeToTopic() {
        client.subscribeWith()
            .topicFilter(config.topic)
            .callback { publish ->
                val payloadStr = publish.payloadAsBytes.toString(Charsets.UTF_8)
                onMessageReceived(config.topic, payloadStr)
            }
            .send()
    }

    private fun onMessageReceived(topic: String, message: String) {
        println("Received MQTT message on topic [$topic]: $message")
    }

    @SuppressLint("NewApi")
    fun disconnect(onDisconnected: (() -> Unit)? = null) {
        if (client.state == MqttClientState.CONNECTED) {
            client.disconnect()
                .whenComplete { _, _ -> onDisconnected?.invoke() }
        } else {
            onDisconnected?.invoke()
        }
    }

    /**
     * Update the config at runtime.
     * If connected, the current client is disconnected first, then a new client is created and connected.
     */
    @SuppressLint("NewApi")
    fun updateConfig(newConfig: MqttConfig, onConnected: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        if (client.state == MqttClientState.CONNECTED) {
            client.disconnect()
                .whenComplete { _, _ ->
                    config = newConfig
                    client = buildClient(config)
                    connect(onConnected, onError)
                }
        } else {
            config = newConfig
            client = buildClient(config)
            connect(onConnected, onError)
        }
    }
}
