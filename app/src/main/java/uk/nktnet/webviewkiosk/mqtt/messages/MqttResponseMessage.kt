package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus

@Serializable
sealed interface MqttResponseMessage {
    val type: String
    val identifier: String? get() = null
    val appInstanceId: String
}

@Serializable
@SerialName("status")
data class MqttStatusResponse(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type: String = "status",
    override val identifier: String? = null,
    override val appInstanceId: String,

    val data: WebviewKioskStatus,
) : MqttResponseMessage
