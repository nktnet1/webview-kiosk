package uk.nktnet.webviewkiosk.mqtt

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MqttEventMessage {
    val event: String
    val identifier: String? get() = null
    val appInstanceId: String
}

@Serializable
@SerialName("url_visited")
data class MqttUrlVisitedEvent(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val event: String = "url_visited",

    val url: String,

    override val identifier: String? = null,
    override val appInstanceId: String,
) : MqttEventMessage

@Serializable
@SerialName("lock")
data class MqttLockEvent(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val event: String = "lock",

    override val identifier: String? = null,
    override val appInstanceId: String,
) : MqttEventMessage

@Serializable
@SerialName("unlock")
data class MqttUnlockEvent(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val event: String = "unlock",

    override val identifier: String? = null,
    override val appInstanceId: String,
) : MqttEventMessage
