package uk.nktnet.webviewkiosk.mqtt

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.config.option.MqttRetainHandlingOption

@Serializable
sealed interface CommandMessage

@Serializable
@SerialName("go_back")
object MqttGoBackCommand : CommandMessage

@Serializable
@SerialName("go_forward")
object MqttGoForwardCommand : CommandMessage

@Serializable
@SerialName("go_home")
object MqttGoHomeCommand : CommandMessage

@Serializable
@SerialName("refresh")
object MqttRefreshCommand : CommandMessage

@Serializable
@SerialName("go_to_url")
data class MqttGoToUrlCommand(val url: String) : CommandMessage

@Serializable
@SerialName("lock")
object MqttLockCommand : CommandMessage

@Serializable
@SerialName("unlock")
object MqttUnlockCommand : CommandMessage

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
