package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.utils.BaseJson

@Serializable
sealed interface MqttCommandMessage {
    val identifier: String? get() = null
}

@Serializable
@SerialName("go_back")
data class MqttGoBackMqttCommand(
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Go Back"
}

@Serializable
@SerialName("go_forward")
data class MqttGoForwardMqttCommand(
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Go Forward"
}

@Serializable
@SerialName("go_home")
data class MqttGoHomeMqttCommand(
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Go Home"
}

@Serializable
@SerialName("refresh")
data class MqttRefreshMqttCommand(
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Refresh"
}

@Serializable
@SerialName("go_to_url")
data class MqttGoToUrlMqttCommand(
    override val identifier: String? = null,
    val data: UrlData,
) : MqttCommandMessage {
    @Serializable
    data class UrlData(
        val url: String
    )

    override fun toString() = "Go to URL"
}

@Serializable
@SerialName("clear_history")
data class MqttClearHistoryMqttCommand(
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Clear History"
}

@Serializable
@SerialName("lock")
data class MqttLockMqttCommand(
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Lock"
}

@Serializable
@SerialName("unlock")
data class MqttUnlockMqttCommand(
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Unlock"
}

@Serializable
@SerialName("reconnect")
data class MqttReconnectMqttCommand(
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Reconnect"
}

@Serializable
@SerialName("error")
data class MqttErrorCommand(
    val error: String = "unknown command",
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Command Error: $error"
}

val MqttCommandJsonParser = Json(BaseJson) {
    classDiscriminator = "command"
}
