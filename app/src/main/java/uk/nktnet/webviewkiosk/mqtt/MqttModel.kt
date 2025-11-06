package uk.nktnet.webviewkiosk.mqtt

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.config.option.MqttRetainHandlingOption

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

data class MqttConfig(
    val enabled: Boolean,
    val clientId: String,
    val serverHost: String,
    val serverPort: Int,
    val username: String,
    val password: String,
    val useTls: Boolean,
    val automaticReconnect: Boolean,
    val cleanStart: Boolean,
    val keepAlive: Int,
    val connectTimeout: Int,
    val subscribeCommandTopic: String,
    val subscribeCommandQos: MqttQosOption,
    val subscribeCommandRetainHandling: MqttRetainHandlingOption,
    val subscribeCommandRetainAsPublished: Boolean,
    val subscribeSettingsTopic: String,
    val subscribeSettingsQos: MqttQosOption,
    val subscribeSettingsRetainHandling: MqttRetainHandlingOption,
    val subscribeSettingsRetainAsPublished: Boolean
)

@OptIn(ExperimentalSerializationApi::class)
val JsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    allowTrailingComma = true
    allowComments = true
    classDiscriminator = "command"
}
