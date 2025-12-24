package com.nktnet.webview_kiosk.config.mqtt.messages

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import com.nktnet.webview_kiosk.config.option.LockStateType
import com.nktnet.webview_kiosk.utils.BaseJson
import com.nktnet.webview_kiosk.utils.WebviewKioskStatus

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

@Serializable
@SerialName("app_foreground")
data class MqttAppForegroundEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "app_foreground"
}

@Serializable
@SerialName("app_background")
data class MqttAppBackgroundEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "app_background"
}

@Serializable
@SerialName("screen_on")
data class MqttScreenOnEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "screen_on"
}

@Serializable
@SerialName("screen_off")
data class MqttScreenOffEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "screen_off"
}

@Serializable
@SerialName("user_present")
data class MqttUserPresentEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "user_present"
}

@Serializable
@SerialName("power_plugged")
data class MqttPowerPluggedEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "power_plugged"
}

@Serializable
@SerialName("power_unplugged")
data class MqttPowerUnpluggedEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String
) : MqttEventMessage {
    override fun getEventType(): String = "power_unplugged"
}

@Serializable
@SerialName("application_restrictions_changed")
data class MqttApplicationRestrictionsChangedEvent(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
) : MqttEventMessage {
    override fun getEventType(): String = "application_restrictions_changed"
}

val MqttEventJsonParser = Json(BaseJson) {
    classDiscriminator = "eventType"
}
