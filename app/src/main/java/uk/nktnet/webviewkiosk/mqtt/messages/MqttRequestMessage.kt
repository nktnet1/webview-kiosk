package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import uk.nktnet.webviewkiosk.utils.BaseJson

@Serializable
sealed interface MqttRequestMessage {
    val identifier: String? get() = null
    var responseTopic: String?
    var correlationData: String?
}

@Serializable
@SerialName("get_status")
data class MqttStatusRequest(
    override val identifier: String? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null
) : MqttRequestMessage {
    override fun toString() = "Get Status"
}

@Serializable
@SerialName("get_settings")
data class MqttSettingsRequest(
    override val identifier: String? = null,
    val settings: Array<JsonElement> = emptyArray(),
    override var responseTopic: String? = null,
    override var correlationData: String? = null
) : MqttRequestMessage {
    override fun toString() = "Get Settings"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MqttSettingsRequest
        return settings.contentEquals(other.settings)
    }
    override fun hashCode(): Int {
        return settings.contentHashCode()
    }
}

@Serializable
@SerialName("get_system_info")
data class MqttSystemInfoRequest(
    override val identifier: String? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null
) : MqttRequestMessage {
    override fun toString() = "Get System Info"
}

@Serializable
@SerialName("error")
data class MqttErrorRequest(
    override val identifier: String? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
    val error: String = "unknown request",
) : MqttRequestMessage {
    override fun toString() = "Request Error: $error"
}

val MqttRequestJsonParser = Json(BaseJson) {
    classDiscriminator = "requestType"
}
