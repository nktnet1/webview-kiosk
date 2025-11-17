package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.Serializable

@Serializable
data class MqttSettingsMessage(
    val identifier: String? = null,
    val applyNow: Boolean = true,
    val showToast: Boolean = true,
    val settings: String,
)
