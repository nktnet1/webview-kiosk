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
    var messageId: String?
    val appInstanceId: String
    fun getType(): String
}

@Serializable
@SerialName("get_status")
data class MqttStatusResponse(
    override var messageId: String? = null,
    override val appInstanceId: String,
    val data: WebviewKioskStatus,
) : MqttResponseMessage {
    override fun getType(): String = "status"
}

@Serializable
@SerialName("get_settings")
data class MqttSettingsResponse(
    override var messageId: String? = null,
    override val appInstanceId: String,
    val data: JsonObject,
) : MqttResponseMessage {
    override fun getType(): String = "settings"
}

@Serializable
@SerialName("get_system_info")
data class MqttSystemInfoResponse(
    override var messageId: String? = null,
    override val appInstanceId: String,
    val data: SystemInfo,
) : MqttResponseMessage {
    override fun getType(): String = "system_info"
}

@Serializable
@SerialName("error")
data class MqttErrorResponse(
    override var messageId: String? = null,
    override val appInstanceId: String,
    val errorMessage: String,
) : MqttResponseMessage {
    override fun getType(): String = "error"
}

val MqttResponseJsonParser = Json(BaseJson) {
    classDiscriminator = "responseType"
}
