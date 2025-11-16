package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import uk.nktnet.webviewkiosk.utils.BaseJson
import uk.nktnet.webviewkiosk.utils.SystemInfo
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus

@Serializable
sealed interface MqttResponseMessage {
    val identifier: String? get() = null
    val appInstanceId: String
    fun getType(): String
}

@Serializable
@SerialName("status")
data class MqttStatusResponse(
    override val identifier: String? = null,
    override val appInstanceId: String,
    val data: WebviewKioskStatus,
) : MqttResponseMessage {
    override fun getType(): String = "status"
}

@Serializable
@SerialName("settings")
data class MqttSettingsResponse(
    override val identifier: String? = null,
    override val appInstanceId: String,
    val data: JsonObject,
) : MqttResponseMessage {
    override fun getType(): String = "settings"
}

@Serializable
@SerialName("system_info")
data class MqttSystemInfoResponse(
    override val identifier: String? = null,
    override val appInstanceId: String,
    val data: SystemInfo,
) : MqttResponseMessage {
    override fun getType(): String = "system_info"
}

val MqttResponseJsonParser = Json(BaseJson) {
    classDiscriminator = "responseType"
}
