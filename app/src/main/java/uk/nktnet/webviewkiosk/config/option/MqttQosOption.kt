package uk.nktnet.webviewkiosk.config.option

import com.hivemq.client.mqtt.datatypes.MqttQos

enum class MqttQosOption(val code: Int, val label: String) {
    AT_MOST_ONCE(0, "At Most Once (0)"),
    AT_LEAST_ONCE(1, "At Least Once (1)"),
    EXACTLY_ONCE(2, "Exactly Once (2)");

    fun toMqttQos(): MqttQos = MqttQos.fromCode(code) ?: MqttQos.AT_MOST_ONCE

    companion object {
        fun fromString(value: String?): MqttQosOption =
            entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
                || it.code.toString() == value
            } ?: AT_MOST_ONCE
    }
}
