package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.utils.BaseJson

@Serializable
sealed interface MqttCommandMessage {
    val messageId: String?
    val interact: Boolean
    val targetInstances: Set<String>?
    val targetUsernames: Set<String>?
}

@Serializable
@SerialName("go_back")
data class MqttGoBackCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
) : MqttCommandMessage {
    override fun toString() = "Go Back"
}

@Serializable
@SerialName("go_forward")
data class MqttGoForwardCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
) : MqttCommandMessage {
    override fun toString() = "Go Forward"
}

@Serializable
@SerialName("go_home")
data class MqttGoHomeCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
) : MqttCommandMessage {
    override fun toString() = "Go Home"
}

@Serializable
@SerialName("refresh")
data class MqttRefreshCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
) : MqttCommandMessage {
    override fun toString() = "Refresh"
}

@Serializable
@SerialName("go_to_url")
data class MqttGoToUrlCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    val data: UrlData,
) : MqttCommandMessage {
    @Serializable
    data class UrlData(
        val url: String
    )
    override fun toString() = "Go to URL"
}

@Serializable
@SerialName("search")
data class MqttSearchCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    val data: QueryData,
) : MqttCommandMessage {
    @Serializable
    data class QueryData(
        val query: String
    )
    override fun toString() = "Search"
}

@Serializable
@SerialName("clear_history")
data class MqttClearHistoryMqttCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
) : MqttCommandMessage {
    override fun toString() = "Clear History"
}

@Serializable
@SerialName("interact")
data class MqttInteractMqttCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
) : MqttCommandMessage {
    override fun toString() = "interact"
}

@Serializable
@SerialName("lock")
data class MqttLockCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
) : MqttCommandMessage {
    override fun toString() = "Lock"
}

@Serializable
@SerialName("unlock")
data class MqttUnlockCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
) : MqttCommandMessage {
    override fun toString() = "Unlock"
}

@Serializable
@SerialName("reconnect")
data class MqttReconnectMqttCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
) : MqttCommandMessage {
    override fun toString() = "Reconnect"
}

@Serializable
@SerialName("lock_device")
data class MqttLockDeviceCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
) : MqttCommandMessage {
    override fun toString() = "Lock Device"
}

@Serializable
@SerialName("error")
data class MqttErrorCommand(
    override val messageId: String? = null,
    override val interact: Boolean = true,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    val error: String = "unknown command",
) : MqttCommandMessage {
    override fun toString() = "Command Error: $error"
}

val MqttCommandJsonParser = Json(BaseJson) {
    classDiscriminator = "command"
}
