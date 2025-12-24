package com.nktnet.webview_kiosk.config.mqtt.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import com.nktnet.webview_kiosk.config.data.SystemInfo
import com.nktnet.webview_kiosk.utils.BaseJson
import com.nktnet.webview_kiosk.utils.WebviewKioskStatus

@Serializable
sealed interface MqttResponseMessage {
    val messageId: String
    val username: String
    val appInstanceId: String
    val requestMessageId: String?
    val correlationData: String?
    fun getType(): String
}

@Serializable
@SerialName("get_status")
data class MqttStatusResponse(
    override val messageId: String,
    override val username: String,
    override val appInstanceId: String,
    override val requestMessageId: String?,
    override val correlationData: String?,
    val data: WebviewKioskStatus,
) : MqttResponseMessage {
    override fun getType(): String = "status"
}

@Serializable
@SerialName("get_settings")
data class MqttSettingsResponse(
    override var messageId: String,
    override val username: String,
    override val appInstanceId: String,
    override val requestMessageId: String?,
    override val correlationData: String?,
    val data: SettingsResponseData,
) : MqttResponseMessage {
    override fun getType(): String = "settings"
    @Serializable
    data class SettingsResponseData(
        val settings: JsonObject,
    )
}

@Serializable
@SerialName("get_system_info")
data class MqttSystemInfoResponse(
    override var messageId: String,
    override val username: String,
    override val appInstanceId: String,
    override val requestMessageId: String?,
    override val correlationData: String?,
    val data: SystemInfo,
) : MqttResponseMessage {
    override fun getType(): String = "system_info"
}

@Serializable
@SerialName("error")
data class MqttErrorResponse(
    override var messageId: String,
    override val username: String,
    override val appInstanceId: String,
    override val requestMessageId: String?,
    override val correlationData: String?,
    val payloadStr: String,
    val errorMessage: String,
) : MqttResponseMessage {
    override fun getType(): String = "error"
}

val MqttResponseJsonParser = Json(BaseJson) {
    classDiscriminator = "responseType"
}
