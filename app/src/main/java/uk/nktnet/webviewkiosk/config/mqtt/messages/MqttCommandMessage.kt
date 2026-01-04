package uk.nktnet.webviewkiosk.config.mqtt.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.config.mqtt.MqttNotifyPriority
import uk.nktnet.webviewkiosk.utils.BaseJson

@Serializable
sealed interface MqttCommandMessage {
    val messageId: String?
    val targetInstances: Set<String>?
    val targetUsernames: Set<String>?
    val interact: Boolean
    val wakeScreen: Boolean
}

@Serializable
@SerialName("go_back")
data class MqttGoBackCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : MqttCommandMessage {
    override fun toString() = "go_back"
}

@Serializable
@SerialName("go_forward")
data class MqttGoForwardCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
) : MqttCommandMessage {
    override fun toString() = "go_forward"
}

@Serializable
@SerialName("go_home")
data class MqttGoHomeCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
) : MqttCommandMessage {
    override fun toString() = "go_home"
}

@Serializable
@SerialName("refresh")
data class MqttRefreshCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
) : MqttCommandMessage {
    override fun toString() = "refresh"
}

@Serializable
@SerialName("go_to_url")
data class MqttGoToUrlCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: UrlData,
) : MqttCommandMessage {
    @Serializable
    data class UrlData(
        val url: String
    )
    override fun toString() = "go_to_url"
}

@Serializable
@SerialName("search")
data class MqttSearchCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: QueryData,
) : MqttCommandMessage {
    @Serializable
    data class QueryData(
        val query: String
    )
    override fun toString() = "search"
}

@Serializable
@SerialName("clear_history")
data class MqttClearHistoryCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : MqttCommandMessage {
    override fun toString() = "clear_history"
}

@Serializable
@SerialName("clear_cache")
data class MqttClearCacheCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
) : MqttCommandMessage {
    override fun toString() = "clear_cache"
}

@Serializable
@SerialName("toast")
data class MqttToastCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: ToastData? = null
) : MqttCommandMessage {
    @Serializable
    data class ToastData(
        val message: String
    )
    override fun toString() = "toast"
}

@Serializable
@SerialName("lock")
data class MqttLockCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : MqttCommandMessage {
    override fun toString() = "lock"
}

@Serializable
@SerialName("unlock")
data class MqttUnlockCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : MqttCommandMessage {
    override fun toString() = "unlock"
}

@Serializable
@SerialName("reconnect")
data class MqttReconnectCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : MqttCommandMessage {
    override fun toString() = "reconnect"
}

@Serializable
@SerialName("lock_device")
data class MqttLockDeviceCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    ) : MqttCommandMessage {
    override fun toString() = "lock_device"
}

@Serializable
@SerialName("page_up")
data class MqttPageUpCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: PageUpData = PageUpData(),
) : MqttCommandMessage {
    @Serializable
    data class PageUpData(
        val absolute: Boolean = false
    )
    override fun toString() = "page_up"
}

@Serializable
@SerialName("page_down")
data class MqttPageDownCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: PageDownData = PageDownData(),
) : MqttCommandMessage {
    @Serializable
    data class PageDownData(
        val absolute: Boolean = false
    )
    override fun toString() = "page_down"
}

@Serializable
@SerialName("notify")
data class MqttNotifyCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: NotifyData = NotifyData(),
) : MqttCommandMessage {
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
data class MqttLaunchPackageCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val data: Data,
) : MqttCommandMessage {
    @Serializable
    data class Data(
        val packageName: String,
        val activityName: String? = null,
    )
    override fun toString() = "launch_package"
}

@Serializable
@SerialName("error")
data class MqttErrorCommand(
    override val messageId: String? = null,
    override val targetInstances: Set<String>? = null,
    override val targetUsernames: Set<String>? = null,
    override val interact: Boolean = true,
    override val wakeScreen: Boolean = false,
    val error: String = "unknown command",
) : MqttCommandMessage {
    override fun toString() = "error"
}

val MqttCommandJsonParser = Json(BaseJson) {
    classDiscriminator = "command"
}
