package com.nktnet.webview_kiosk.config.mqtt.messages

import kotlinx.serialization.Serializable

@Serializable
data class MqttSettingsMessage(
    val messageId: String? = null,
    val targetInstances: Set<String>? = null,
    val targetUsernames: Set<String>? = null,
    val showToast: Boolean = true,
    val reloadActivity: Boolean = true,
    val data: SettingsUpdateData,
) {
    @Serializable
    data class SettingsUpdateData(
        val settings: String,
    )
}
