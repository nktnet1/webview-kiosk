package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.Serializable

@Serializable
data class MqttSettingsMessage(
    val messageId: String? = null,
    val refresh: Boolean = true,
    val showToast: Boolean = true,
    val settings: String,
)
