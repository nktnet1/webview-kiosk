package uk.nktnet.webviewkiosk.config.remote.inbound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.config.mqtt.MqttNotifyPriority
import uk.nktnet.webviewkiosk.utils.BaseJson

@Serializable
sealed interface InboundCommandMessage {
    val messageId: String?
    val targetInstances: Set<String>?
    val targetUsernames: Set<String>?
    val interact: Boolean
    val wakeScreen: Boolean
}

@Serializable
@SerialName("go_back")
data class InboundGoBackCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : InboundCommandMessage {
    override fun toString() = "go_back"
}

@Serializable
@SerialName("go_forward")
data class InboundGoForwardCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
) : InboundCommandMessage {
    override fun toString() = "go_forward"
}

@Serializable
@SerialName("go_home")
data class InboundGoHomeCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
) : InboundCommandMessage {
    override fun toString() = "go_home"
}

@Serializable
@SerialName("refresh")
data class InboundRefreshCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
) : InboundCommandMessage {
    override fun toString() = "refresh"
}

@Serializable
@SerialName("go_to_url")
data class InboundGoToUrlCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: UrlData,
) : InboundCommandMessage {
    @Serializable
    data class UrlData(
        val url: String
    )
    override fun toString() = "go_to_url"
}

@Serializable
@SerialName("search")
data class InboundSearchCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: QueryData,
) : InboundCommandMessage {
    @Serializable
    data class QueryData(
        val query: String
    )
    override fun toString() = "search"
}

@Serializable
@SerialName("clear_history")
data class InboundClearHistoryCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : InboundCommandMessage {
    override fun toString() = "clear_history"
}

@Serializable
@SerialName("clear_cache")
data class InboundClearCacheCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
) : InboundCommandMessage {
    override fun toString() = "clear_cache"
}

@Serializable
@SerialName("toast")
data class InboundToastCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: ToastData? = null
) : InboundCommandMessage {
    @Serializable
    data class ToastData(
        val message: String
    )
    override fun toString() = "toast"
}

@Serializable
@SerialName("lock")
data class InboundLockCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : InboundCommandMessage {
    override fun toString() = "lock"
}

@Serializable
@SerialName("unlock")
data class InboundUnlockCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : InboundCommandMessage {
    override fun toString() = "unlock"
}

@Serializable
@SerialName("reconnect")
data class InboundReconnectCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : InboundCommandMessage {
    override fun toString() = "reconnect"
}

@Serializable
@SerialName("lock_device")
data class InboundLockDeviceCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : InboundCommandMessage {
    override fun toString() = "lock_device"
}

@Serializable
@SerialName("page_up")
data class InboundPageUpCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: PageUpData = PageUpData(),
) : InboundCommandMessage {
    @Serializable
    data class PageUpData(
        val absolute: Boolean = false
    )
    override fun toString() = "page_up"
}

@Serializable
@SerialName("page_down")
data class InboundPageDownCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: PageDownData = PageDownData(),
) : InboundCommandMessage {
    @Serializable
    data class PageDownData(
        val absolute: Boolean = false
    )
    override fun toString() = "page_down"
}

@Serializable
@SerialName("notify")
data class InboundNotifyCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: NotifyData = NotifyData(),
) : InboundCommandMessage {
    @Serializable
    data class NotifyData(
        val contentTitle: String = "MQTT",
        val contentText: String = "Notify",
        val silent: Boolean = false,
        val onGoing: Boolean = false,
        val priority: MqttNotifyPriority = MqttNotifyPriority.DEFAULT,
        val timeout: Long = 0,
        val autoCancel: Boolean = true,
    )
    override fun toString() = "notify"
}

@Serializable
@SerialName("launch_package")
data class InboundLaunchPackageCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: Data,
) : InboundCommandMessage {
    @Serializable
    data class Data(
        val packageName: String,
        val activityName: String? = null,
    )
    override fun toString() = "launch_package"
}

@Serializable
@SerialName("error")
data class InboundErrorCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val error: String = "unknown command",
) : InboundCommandMessage {
    override fun toString() = "error"
}

val InboundCommandJsonParser = Json(BaseJson) {
    classDiscriminator = "command"
}
