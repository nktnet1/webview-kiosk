package uk.nktnet.webviewkiosk.config.remote.outbound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.config.option.LockStateType
import uk.nktnet.webviewkiosk.utils.BaseJson
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus

@Serializable
sealed interface OutboundEventMessage {
    val messageId: String
    val username: String
    val appInstanceId: String
    fun getEventType(): String
}

@Serializable
@SerialName("connected")
data class OutboundConnectedEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
    val data: WebviewKioskStatus,
) : OutboundEventMessage {
    override fun getEventType(): String = "connected"
}

@Serializable
@SerialName("disconnecting")
data class OutboundDisconnectingEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
    val data: DisconnectingData
) : OutboundEventMessage {
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
data class OutboundUrlChangedEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
    val data: UrlData
) : OutboundEventMessage {
    override fun getEventType(): String = "url_changed"

    @Serializable
    data class UrlData(
        val url: String
    )
}

@Serializable
@SerialName("lock")
data class OutboundLockEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
    val data: LockStateData,
) : OutboundEventMessage {
    override fun getEventType(): String = "lock"

    @Serializable
    data class LockStateData(
        val lockStateType: LockStateType
    )
}

@Serializable
@SerialName("unlock")
data class OutboundUnlockEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : OutboundEventMessage {
    override fun getEventType(): String = "unlock"
}

@Serializable
@SerialName("app_foreground")
data class OutboundAppForegroundEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : OutboundEventMessage {
    override fun getEventType(): String = "app_foreground"
}

@Serializable
@SerialName("app_background")
data class OutboundAppBackgroundEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : OutboundEventMessage {
    override fun getEventType(): String = "app_background"
}

@Serializable
@SerialName("screen_on")
data class OutboundScreenOnEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : OutboundEventMessage {
    override fun getEventType(): String = "screen_on"
}

@Serializable
@SerialName("screen_off")
data class OutboundScreenOffEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : OutboundEventMessage {
    override fun getEventType(): String = "screen_off"
}

@Serializable
@SerialName("user_present")
data class OutboundUserPresentEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : OutboundEventMessage {
    override fun getEventType(): String = "user_present"
}

@Serializable
@SerialName("power_plugged")
data class OutboundPowerPluggedEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : OutboundEventMessage {
    override fun getEventType(): String = "power_plugged"
}

@Serializable
@SerialName("power_unplugged")
data class OutboundPowerUnpluggedEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String
) : OutboundEventMessage {
    override fun getEventType(): String = "power_unplugged"
}

@Serializable
@SerialName("application_restrictions_changed")
data class OutboundApplicationRestrictionsChangedEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : OutboundEventMessage {
    override fun getEventType(): String = "application_restrictions_changed"
}

val OutboundEventJsonParser = Json(BaseJson) {
    classDiscriminator = "eventType"
}
