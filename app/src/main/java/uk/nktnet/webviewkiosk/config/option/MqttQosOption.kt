package uk.nktnet.webviewkiosk.config.option

import com.hivemq.client.mqtt.datatypes.MqttQos

enum class MqttQosOption(val code: Int) {
    AT_MOST_ONCE(0),
    AT_LEAST_ONCE(1),
    EXACTLY_ONCE(2);

    fun toMqttQos(): MqttQos = MqttQos.fromCode(code) ?: MqttQos.AT_MOST_ONCE

    companion object {
        fun fromCode(value: Int?): MqttQosOption =
            entries.find { it.code == value } ?: AT_MOST_ONCE
    }
}
