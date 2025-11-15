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
    val url: String,
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Go to URL: $url"
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
@SerialName("get_status")
data class MqttGetStatusCommand(
    override val identifier: String? = null,
) : MqttGetBaseCommand() {
    override fun toString() = "Get Status"
}

@Serializable
@SerialName("get_settings")
data class MqttGetSettingsCommand(
    override val identifier: String? = null,
    val settingKeys: Array<String> = emptyArray()
) : MqttGetBaseCommand() {
    override fun toString() = "Get Settings"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MqttGetSettingsCommand
        return settingKeys.contentEquals(other.settingKeys)
    }
    override fun hashCode(): Int {
        return settingKeys.contentHashCode()
    }
}

@Serializable
@SerialName("error")
data class MqttMqttCommandError(
    val error: String = "unknown command",
    override val identifier: String? = null,
) : MqttCommandMessage {
    override fun toString() = "Command Error: $error"
}

val MqttCommandJsonParser = Json(BaseJson) {
    classDiscriminator = "command"
}
