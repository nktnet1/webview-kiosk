package uk.nktnet.webviewkiosk.mqtt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MqttEventMessage {
    val identifier: String? get() = null
}

@Serializable
@SerialName("url_change")
data class MqttUrlChangeEvent(
    val url: String,
    override val identifier: String? = null
) : MqttEventMessage {
    override fun toString() = "URL Change: $url"
}
