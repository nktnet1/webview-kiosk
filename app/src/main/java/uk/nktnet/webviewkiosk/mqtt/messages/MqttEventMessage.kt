package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.utils.BaseJson
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus

@Serializable
sealed interface MqttEventMessage {
    val identifier: String? get() = null
    val appInstanceId: String
    fun getName(): String
}

@Serializable
@SerialName("connected")
data class MqttConnectedEvent(
    override val identifier: String? = null,
    override val appInstanceId: String,
    val data: WebviewKioskStatus,
) : MqttEventMessage {
    override fun getName(): String = "connected"
}

@Serializable
@SerialName("url_visited")
data class MqttUrlVisitedEvent(
    override val identifier: String? = null,
    override val appInstanceId: String,
    val url: String,
) : MqttEventMessage {
    override fun getName(): String = "url_visited"
}

@Serializable
@SerialName("lock")
data class MqttLockEvent(
    override val identifier: String? = null,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getName(): String = "lock"
}

@Serializable
@SerialName("unlock")
data class MqttUnlockEvent(
    override val identifier: String? = null,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getName(): String = "unlock"
}

val MqttEventJsonParser = Json(BaseJson) {
    classDiscriminator = "event"
}
