package uk.nktnet.webviewkiosk.config.mqtt.messages

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.config.option.LockStateType
import uk.nktnet.webviewkiosk.utils.BaseJson
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus

@Serializable
sealed interface MqttEventMessage {
    val messageId: String
    val username: String
    val appInstanceId: String
    fun getEventType(): String
}

@Serializable
@SerialName("connected")
data class MqttConnectedEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
    val data: WebviewKioskStatus,
) : MqttEventMessage {
    override fun getEventType(): String = "connected"
}

@Serializable
@SerialName("disconnecting")
data class MqttDisconnectingEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
    val data: DisconnectingData
) : MqttEventMessage {
    @Serializable
    data class DisconnectingData(
        val cause: DisconnectCause
    )
    @Serializable
    enum class DisconnectCause {
        USER_INITIATED_DISCONNECT,
        USER_INITIATED_RESTART,
        USER_INITIATED_SETTINGS_DISABLED,
        SYSTEM_ACTIVITY_STOPPED,
        SYSTEM_ACTIVITY_DESTROYED,
        MQTT_RECONNECT_COMMAND_RECEIVED,
    }
    override fun getEventType(): String = "disconnecting"
}

@Serializable
@SerialName("url_changed")
data class MqttUrlChangedEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
    val data: UrlData
) : MqttEventMessage {
    override fun getEventType(): String = "url_changed"

    @Serializable
    data class UrlData(
        val url: String
    )
}

@Serializable
@SerialName("lock")
data class MqttLockEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
    val data: LockStateData,
) : MqttEventMessage {
    override fun getEventType(): String = "lock"

    @Serializable
    data class LockStateData(
        val lockStateType: LockStateType
    )
}

@Serializable
@SerialName("unlock")
data class MqttUnlockEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "unlock"
}

val MqttEventJsonParser = Json(BaseJson) {
    classDiscriminator = "eventType"
}
