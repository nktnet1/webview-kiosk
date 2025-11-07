package uk.nktnet.webviewkiosk.mqtt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface CommandMessage {
    val identifier: String? get() = null
}

@Serializable
@SerialName("go_back")
data class MqttGoBackCommand(override val identifier: String? = null) : CommandMessage {
    override fun toString() = "Go Back"
}

@Serializable
@SerialName("go_forward")
data class MqttGoForwardCommand(override val identifier: String? = null) : CommandMessage {
    override fun toString() = "Go Forward"
}

@Serializable
@SerialName("go_home")
data class MqttGoHomeCommand(override val identifier: String? = null) : CommandMessage {
    override fun toString() = "Go Home"
}

@Serializable
@SerialName("refresh")
data class MqttRefreshCommand(override val identifier: String? = null) : CommandMessage {
    override fun toString() = "Refresh"
}

@Serializable
@SerialName("go_to_url")
data class MqttGoToUrlCommand(
    val url: String,
    override val identifier: String? = null
) : CommandMessage {
    override fun toString() = "Go to URL: $url"
}

@Serializable
@SerialName("lock")
data class MqttLockCommand(override val identifier: String? = null) : CommandMessage {
    override fun toString() = "Lock"
}

@Serializable
@SerialName("unlock")
data class MqttUnlockCommand(override val identifier: String? = null) : CommandMessage {
    override fun toString() = "Unlock"
}

@Serializable
@SerialName("error")
data class MqttCommandError(
    val error: String = "unknown command",
    override val identifier: String? = null
) : CommandMessage {
    override fun toString() = "Command Error: $error"
}

@OptIn(ExperimentalSerializationApi::class)
val MqttCommandJsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    allowTrailingComma = true
    allowComments = true
    classDiscriminator = "command"
}
