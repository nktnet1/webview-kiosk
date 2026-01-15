package uk.nktnet.webviewkiosk.config.remote.inbound

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class InboundSettingsMessage(
    val messageId: String? = null,
    val targetInstances: Set<String>? = null,
    val targetUsernames: Set<String>? = null,
    val showToast: Boolean = true,
    val reloadActivity: Boolean = true,
    val data: SettingsUpdateData = SettingsUpdateData(),
) {
    @Serializable
    data class SettingsUpdateData(
        val settings: JsonObject = buildJsonObject {},
    )
}
