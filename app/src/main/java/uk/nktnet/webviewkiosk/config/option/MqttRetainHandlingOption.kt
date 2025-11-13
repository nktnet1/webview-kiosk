package uk.nktnet.webviewkiosk.config.option

import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling

enum class MqttRetainHandlingOption(val code: Int, val label: String) {
    SEND(0, "Send"),
    SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST(1, "Send If Subscription Does Not Exist"),
    DO_NOT_SEND(2, "Do Not Send");

    fun toMqttRetainHandling(): Mqtt5RetainHandling =
        Mqtt5RetainHandling.fromCode(code) ?: Mqtt5RetainHandling.DO_NOT_SEND

    companion object {
        fun fromString(value: String?): MqttRetainHandlingOption =
            entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
                || it.code.toString() == value
            } ?: DO_NOT_SEND
    }
}
