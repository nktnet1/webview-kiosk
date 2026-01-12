package uk.nktnet.webviewkiosk.config.remote.inbound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import uk.nktnet.webviewkiosk.utils.BaseJson

@Serializable
sealed interface InboundRequestMessage {
    val messageId: String?
    val targetInstances: Set<String>?
    val targetUsernames: Set<String>?
    var responseTopic: String?
    var correlationData: String?
}

@Serializable
@SerialName("get_status")
data class InboundStatusRequest(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
) : InboundRequestMessage {
    override fun toString() = "get_status"
}

@Serializable
@SerialName("get_settings")
data class InboundSettingsRequest(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
    val data: SettingsRequestData = SettingsRequestData(),
) : InboundRequestMessage {
    @Serializable
    data class SettingsRequestData(
        val settings: Array<JsonElement> = emptyArray(),
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SettingsRequestData
            return settings.contentEquals(other.settings)
        }
        override fun hashCode(): Int {
            return settings.contentHashCode()
        }
    }
}

@Serializable
@SerialName("get_system_info")
data class InboundSystemInfoRequest(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
) : InboundRequestMessage {
    override fun toString() = "get_system_info"
}

@Serializable
@SerialName("get_launchable_packages")
data class InboundLaunchablePackagesRequest(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
    val data: Data = Data()
) : InboundRequestMessage {
    override fun toString() = "get_launchable_packages"
    @Serializable
    data class Data(
        val filterLockTaskPermitted: Boolean = false,
    )
}

@Serializable
@SerialName("get_lock_task_packages")
data class InboundLockTaskPackagesRequest(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
) : InboundRequestMessage {
    override fun toString() = "get_lock_task_packages"
}

@Serializable
@SerialName("error")
data class InboundErrorRequest(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override var responseTopic: String? = null,
    override var correlationData: String? = null,
    val payloadStr: String,
    val error: String,
) : InboundRequestMessage {
    override fun toString() = "Request Error: $error"
}

val OutboundRequestJsonParser = Json(BaseJson) {
    classDiscriminator = "requestType"
}
