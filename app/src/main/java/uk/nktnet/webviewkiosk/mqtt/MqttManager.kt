package uk.nktnet.webviewkiosk.mqtt

import android.annotation.SuppressLint
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.MqttQosOption

open class MqttSubscriber(private val userSettings: UserSettings) {
    private var client: Mqtt5AsyncClient = buildClient()

    private fun buildClient(): Mqtt5AsyncClient {
        return Mqtt5Client.builder()
            .identifier(userSettings.mqttClientId)
            .serverHost(userSettings.mqttBrokerUrl)
            .serverPort(userSettings.mqttPort)
            .apply {
                if (userSettings.mqttUsername.isNotEmpty()) {
                    simpleAuth()
                        .username(userSettings.mqttUsername)
                        .password(userSettings.mqttPassword.toByteArray())
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
                    subscribeToTopics()
                }
            }
    }

    private fun subscribeToTopics() {
        subscribe(userSettings.mqttSubscribeCommandTopic, userSettings.mqttSubscribeCommandQos)
        subscribe(userSettings.mqttSubscribeSettingsTopic, userSettings.mqttSubscribeSettingsQos)
    }

    private fun subscribe(topic: String, qos: MqttQosOption) {
        client.subscribeWith()
            .topicFilter(topic)
            .qos(qos.toMqttQos())
            .callback { publish ->
                val payloadStr = publish.payloadAsBytes.toString(Charsets.UTF_8)
                onMessageReceived(topic, payloadStr)
            }
            .send()
    }

    protected open fun onMessageReceived(topic: String, message: String) {
        println("Received MQTT message on topic [$topic]: $message")
    }

    @SuppressLint("NewApi")
    fun disconnect(onDisconnected: (() -> Unit)? = null) {
        if (client.state == MqttClientState.CONNECTED) {
            client.disconnect().whenComplete { _, _ -> onDisconnected?.invoke() }
        } else {
            onDisconnected?.invoke()
        }
    }

    @SuppressLint("NewApi")
    fun refreshConfig(onConnected: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        if (client.state == MqttClientState.CONNECTED) {
            client.disconnect().whenComplete { _, _ ->
                client = buildClient()
                connect(onConnected, onError)
            }
        } else {
            client = buildClient()
            connect(onConnected, onError)
        }
    }
}
