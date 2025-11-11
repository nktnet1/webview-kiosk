package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface MqttCommandMessage {
    val identifier: String? get() = null
}

@Serializable
@SerialName("go_back")
data class MqttGoBackMqttCommand(override val identifier: String? = null) : MqttCommandMessage {
    override fun toString() = "Go Back"
}

@Serializable
@SerialName("go_forward")
data class MqttGoForwardMqttCommand(override val identifier: String? = null) : MqttCommandMessage {
    override fun toString() = "Go Forward"
}

@Serializable
@SerialName("go_home")
data class MqttGoHomeMqttCommand(override val identifier: String? = null) : MqttCommandMessage {
    override fun toString() = "Go Home"
}

@Serializable
@SerialName("refresh")
data class MqttRefreshMqttCommand(override val identifier: String? = null) : MqttCommandMessage {
    override fun toString() = "Refresh"
}

@Serializable
@SerialName("go_to_url")
data class MqttGoToUrlMqttCommand(
    val url: String,
    override val identifier: String? = null
) : MqttCommandMessage {
    override fun toString() = "Go to URL: $url"
}

@Serializable
@SerialName("lock")
data class MqttLockMqttCommand(override val identifier: String? = null) : MqttCommandMessage {
    override fun toString() = "Lock"
}

@Serializable
@SerialName("unlock")
data class MqttUnlockMqttCommand(override val identifier: String? = null) : MqttCommandMessage {
    override fun toString() = "Unlock"
}

@Serializable
@SerialName("get_status")
data class MqttGetStatusMqttCommand(
    override val identifier: String? = null,
    var responseTopic: String? = null,
    var correlationData: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Get Status"
}

@Serializable
@SerialName("error")
data class MqttMqttCommandError(
    val error: String = "unknown command",
    override val identifier: String? = null
) : MqttCommandMessage {
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
