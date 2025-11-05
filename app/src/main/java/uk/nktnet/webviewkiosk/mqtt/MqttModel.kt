package uk.nktnet.webviewkiosk.mqtt

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.config.option.MqttRetainHandlingOption

@Serializable
sealed interface CommandMessage

@Serializable
@SerialName("go_back")
data class MqttGoBackCommand(val unused: Unit = Unit) : CommandMessage

@Serializable
@SerialName("go_forward")
data class MqttGoForwardCommand(val unused: Unit = Unit) : CommandMessage

@Serializable
@SerialName("go_home")
data class MqttGoHomeCommand(val unused: Unit = Unit) : CommandMessage

@Serializable
@SerialName("refresh")
data class MqttRefreshCommand(val unused: Unit = Unit) : CommandMessage

@Serializable
@SerialName("go_to_url")
data class MqttGoToUrlCommand(val url: String) : CommandMessage

@Serializable
@SerialName("lock")
data class MqttLockCommand(val unused: Unit = Unit) : CommandMessage

@Serializable
@SerialName("unlock")
data class MqttUnlockCommand(val unused: Unit = Unit) : CommandMessage

data class MqttConfig(
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

val JsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    classDiscriminator = "command"
}
