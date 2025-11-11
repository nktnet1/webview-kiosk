package uk.nktnet.webviewkiosk.mqtt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MqttEventMessage {
    val event: String
    val identifier: String?
}

@Serializable
@SerialName("url_visited")
data class MqttUrlVisitedEvent(
    @OptIn(ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault
    override val event: String = "url_visited",

    val url: String,
    override val identifier: String? = null,
) : MqttEventMessage
