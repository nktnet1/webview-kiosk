package com.nktnet.webview_kiosk.config.mqtt

import com.hivemq.client.mqtt.datatypes.MqttQos

enum class MqttQosOption(val code: Int, val label: String) {
    AT_MOST_ONCE(0, "At Most Once"),
    AT_LEAST_ONCE(1, "At Least Once"),
    EXACTLY_ONCE(2, "Exactly Once");

    fun toMqttQos(): MqttQos = MqttQos.fromCode(code) ?: MqttQos.AT_MOST_ONCE

    fun getSettingLabel(): String = "$label ($code)"

    companion object {
        fun fromString(value: String?): MqttQosOption =
            entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
                || it.code.toString() == value
                || it.getSettingLabel() == value
            } ?: AT_MOST_ONCE
    }
}
