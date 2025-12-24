package com.nktnet.webview_kiosk.config.mqtt.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import com.nktnet.webview_kiosk.utils.BaseJson

@Serializable
sealed interface MqttRequestMessage {
    val messageId: String?
    val targetInstances: Set<String>?
    val targetUsernames: Set<String>?
    var responseTopic: String?
    var correlationData: String?
}

@Serializable
@SerialName("get_status")
data class MqttStatusRequest(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
) : MqttRequestMessage {
    override fun toString() = "Get Status"
}

@Serializable
@SerialName("get_settings")
data class MqttSettingsRequest(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
    val data: SettingsRequestData = SettingsRequestData(),
) : MqttRequestMessage {
    @Serializable
    data class SettingsRequestData(
        val settings: Array<JsonElement> = emptyArray(),
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SettingsRequestData
            return settings.contentEquals(other.settings)
        }
        override fun hashCode(): Int {
            return settings.contentHashCode()
        }
    }
}

@Serializable
@SerialName("get_system_info")
data class MqttSystemInfoRequest(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
) : MqttRequestMessage {
    override fun toString() = "Get System Info"
}

@Serializable
@SerialName("error")
data class MqttErrorRequest(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
    val payloadStr: String,
    val error: String,
) : MqttRequestMessage {
    override fun toString() = "Request Error: $error"
}

val MqttRequestJsonParser = Json(BaseJson) {
    classDiscriminator = "requestType"
}
