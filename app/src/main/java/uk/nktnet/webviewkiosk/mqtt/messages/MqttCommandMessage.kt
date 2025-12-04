package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.utils.BaseJson

@Serializable
sealed interface MqttCommandMessage {
    val messageId: String?
    val targetInstances: Set<String>?
    val targetUsernames: Set<String>?
    val interact: Boolean
}

@Serializable
@SerialName("go_back")
data class MqttGoBackCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
) : MqttCommandMessage {
    override fun toString() = "go_back"
}

@Serializable
@SerialName("go_forward")
data class MqttGoForwardCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
) : MqttCommandMessage {
    override fun toString() = "go_forward"
}

@Serializable
@SerialName("go_home")
data class MqttGoHomeCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
) : MqttCommandMessage {
    override fun toString() = "go_home"
}

@Serializable
@SerialName("refresh")
data class MqttRefreshCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
) : MqttCommandMessage {
    override fun toString() = "refresh"
}

@Serializable
@SerialName("go_to_url")
data class MqttGoToUrlCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    val data: UrlData,
) : MqttCommandMessage {
    @Serializable
    data class UrlData(
        val url: String
    )
    override fun toString() = "go_to_url"
}

@Serializable
@SerialName("search")
data class MqttSearchCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    val data: QueryData,
) : MqttCommandMessage {
    @Serializable
    data class QueryData(
        val query: String
    )
    override fun toString() = "search"
}

@Serializable
@SerialName("clear_history")
data class MqttClearHistoryMqttCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
) : MqttCommandMessage {
    override fun toString() = "clear_history"
}

@Serializable
@SerialName("interact")
data class MqttInteractMqttCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
) : MqttCommandMessage {
    override fun toString() = "interact"
}

@Serializable
@SerialName("lock")
data class MqttLockCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
) : MqttCommandMessage {
    override fun toString() = "lock"
}

@Serializable
@SerialName("unlock")
data class MqttUnlockCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
) : MqttCommandMessage {
    override fun toString() = "unlock"
}

@Serializable
@SerialName("reconnect")
data class MqttReconnectMqttCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
) : MqttCommandMessage {
    override fun toString() = "reconnect"
}

@Serializable
@SerialName("lock_device")
data class MqttLockDeviceCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
) : MqttCommandMessage {
    override fun toString() = "lock_device"
}

@Serializable
@SerialName("error")
data class MqttErrorCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    val error: String = "unknown command",
) : MqttCommandMessage {
    override fun toString() = "error"
}

val MqttCommandJsonParser = Json(BaseJson) {
    classDiscriminator = "command"
}
