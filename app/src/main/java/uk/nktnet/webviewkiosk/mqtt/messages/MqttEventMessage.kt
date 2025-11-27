package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.utils.BaseJson
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus

@Serializable
sealed interface MqttEventMessage {
    val messageId: String? get() = null
    val appInstanceId: String
    fun getEventType(): String
}

@Serializable
@SerialName("connected")
data class MqttConnectedEvent(
    override val messageId: String? = null,
    override val appInstanceId: String,
    val data: WebviewKioskStatus,
) : MqttEventMessage {
    override fun getEventType(): String = "connected"
}

@Serializable
@SerialName("disconnected")
data class MqttDisconnectingEvent(
    override val messageId: String? = null,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "disconnecting"
}

@Serializable
@SerialName("url_visited")
data class MqttUrlVisitedEvent(
    override val messageId: String? = null,
    override val appInstanceId: String,
    val data: UrlData
) : MqttEventMessage {
    override fun getEventType(): String = "url_visited"

    @Serializable
    data class UrlData(
        val url: String
    )
}

@Serializable
@SerialName("lock")
data class MqttLockEvent(
    override val messageId: String? = null,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "lock"
}

@Serializable
@SerialName("unlock")
data class MqttUnlockEvent(
    override val messageId: String? = null,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "unlock"
}

val MqttEventJsonParser = Json(BaseJson) {
    classDiscriminator = "eventType"
}
