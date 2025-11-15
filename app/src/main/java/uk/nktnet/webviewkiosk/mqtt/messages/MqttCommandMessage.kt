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
sealed class MqttGetBaseCommand(
    @SerialName("responseTopic") open var responseTopic: String? = null,
    @SerialName("correlationData") open var correlationData: String? = null
) : MqttCommandMessage

@Serializable
@SerialName("go_back")
class MqttGoBackMqttCommand() : MqttCommandMessage {
    override fun toString() = "Go Back"
}

@Serializable
@SerialName("go_forward")
class MqttGoForwardMqttCommand() : MqttCommandMessage {
    override fun toString() = "Go Forward"
}

@Serializable
@SerialName("go_home")
class MqttGoHomeMqttCommand() : MqttCommandMessage {
    override fun toString() = "Go Home"
}

@Serializable
@SerialName("refresh")
class MqttRefreshMqttCommand() : MqttCommandMessage {
    override fun toString() = "Refresh"
}

@Serializable
@SerialName("go_to_url")
class MqttGoToUrlMqttCommand(
    val url: String,
) : MqttCommandMessage {
    override fun toString() = "Go to URL: $url"
}

@Serializable
@SerialName("lock")
class MqttLockMqttCommand() : MqttCommandMessage {
    override fun toString() = "Lock"
}

@Serializable
@SerialName("unlock")
class MqttUnlockMqttCommand() : MqttCommandMessage {
    override fun toString() = "Unlock"
}

@Serializable
@SerialName("get_status")
class MqttGetStatusCommand : MqttGetBaseCommand() {
    override fun toString() = "Get Status"
}

@Serializable
@SerialName("get_settings")
class MqttGetSettingsCommand(
    val settingKeys: Array<String> = emptyArray()
) : MqttGetBaseCommand() {
    override fun toString() = "Get Settings"
}

@Serializable
@SerialName("error")
class MqttMqttCommandError(
    val error: String = "unknown command",
) : MqttCommandMessage {
    override fun toString() = "Command Error: $error"
}

val MqttCommandJsonParser = Json(BaseJson) {
    classDiscriminator = "command"
}
