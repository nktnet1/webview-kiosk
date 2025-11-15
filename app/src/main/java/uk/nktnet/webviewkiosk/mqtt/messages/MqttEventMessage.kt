package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.utils.BaseJson

@Serializable
sealed interface MqttEventMessage {
    val identifier: String? get() = null
    val appInstanceId: String
    fun getName(): String
}

@Serializable
@SerialName("url_visited")
data class MqttUrlVisitedEvent(
    val url: String,
    override val identifier: String? = null,
    override val appInstanceId: String
) : MqttEventMessage {
    override fun getName(): String = "url_visited"
}

@Serializable
@SerialName("lock")
data class MqttLockEvent(
    override val identifier: String? = null,
    override val appInstanceId: String
) : MqttEventMessage {
    override fun getName(): String = "lock"
}

@Serializable
@SerialName("unlock")
data class MqttUnlockEvent(
    override val identifier: String? = null,
    override val appInstanceId: String
) : MqttEventMessage {
    override fun getName(): String = "unlock"
}

val MqttEventJsonParser = Json(BaseJson) {
    classDiscriminator = "event"
}
