package com.nktnet.webview_kiosk.config.mqtt

import androidx.core.app.NotificationCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = MqttNotifyPrioritySerializer::class)
enum class MqttNotifyPriority(val androidValue: Int, val label: String) {
    MIN(NotificationCompat.PRIORITY_MIN, "Min"),
    LOW(NotificationCompat.PRIORITY_LOW, "Low"),
    DEFAULT(NotificationCompat.PRIORITY_DEFAULT, "Default"),
    HIGH(NotificationCompat.PRIORITY_HIGH, "High"),
    MAX(NotificationCompat.PRIORITY_MAX, "Max");

    companion object {
        fun fromString(value: String?): MqttNotifyPriority =
            entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
                || it.androidValue.toString() == value
            } ?: DEFAULT
    }
}

object MqttNotifyPrioritySerializer : KSerializer<MqttNotifyPriority> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "MqttNotifyPriority",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: MqttNotifyPriority) {
        encoder.encodeString(value.label)
    }

    override fun deserialize(decoder: Decoder): MqttNotifyPriority {
        val str = decoder.decodeString()
        return MqttNotifyPriority.fromString(str)
    }
}
