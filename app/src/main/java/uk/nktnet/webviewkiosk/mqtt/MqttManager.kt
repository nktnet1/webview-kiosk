package uk.nktnet.webviewkiosk.mqtt

import android.annotation.SuppressLint
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets.UTF_8

open class MqttManager(private val userSettings: UserSettings) {
    private var client: Mqtt5AsyncClient = buildClient()

    private fun buildClient(): Mqtt5AsyncClient {
        var builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier(userSettings.mqttClientId)
            .serverHost(userSettings.mqttServerHost)
            .serverPort(userSettings.mqttServerPort)

        /**
         * TODO: make this configurable
         */
        builder = builder.sslWithDefaultConfig()

        return builder
            .transportConfig()
                .mqttConnectTimeout(userSettings.mqttConnectionTimeout * 1L, TimeUnit.SECONDS)
                .applyTransportConfig()
            .buildAsync()
    }

    @SuppressLint("NewApi")
    fun connect(onConnected: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        client.connectWith()
            .simpleAuth()
                .username(userSettings.mqttUsername)
                .password(UTF_8.encode(userSettings.mqttPassword))
                .applySimpleAuth()
            .send()
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
                val payloadStr = publish.payloadAsBytes.toString(UTF_8)
                onMessageReceived(topic, payloadStr)
            }
            .send()
    }

    protected open fun onMessageReceived(topic: String, message: String) {
        println("[MQTT] Received MQTT message on topic [$topic]: $message")
    }

    @SuppressLint("NewApi")
    fun disconnect(onDisconnected: (() -> Unit)? = null) {
        if (client.state == MqttClientState.CONNECTED) {
            client.disconnect().whenComplete { _, _ -> onDisconnected?.invoke() }
        } else {
            onDisconnected?.invoke()
        }
    }
}
