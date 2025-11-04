package uk.nktnet.webviewkiosk.config.option

import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling

enum class MqttRetainHandlingOption(val code: Int) {
    SEND(0),
    SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST(1),
    DO_NOT_SEND(2);

    fun toMqttRetainHandling(): Mqtt5RetainHandling = Mqtt5RetainHandling.fromCode(code)
        ?: Mqtt5RetainHandling.DO_NOT_SEND

    companion object {
        fun fromCode(value: Int?): MqttRetainHandlingOption =
            entries.find { it.code == value } ?: DO_NOT_SEND
    }
}
