package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.json.JSONObject
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus

@Serializable
sealed interface MqttResponseMessage {
    val type: String
    val identifier: String? get() = null
    val appInstanceId: String
}

@Serializable
@SerialName("status")
data class MqttStatusResponse(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type: String = "status",
    override val identifier: String? = null,
    override val appInstanceId: String,
    val data: WebviewKioskStatus,
) : MqttResponseMessage

@Serializable
@SerialName("settings")
data class MqttSettingsResponse(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type: String = "settings",
    override val identifier: String? = null,
    override val appInstanceId: String,
    val data: JsonObject,
) : MqttResponseMessage

fun JSONObject.toFilteredJsonObject(filterKeys: Array<String> = emptyArray()): JsonObject {
    val filtered = if (filterKeys.isEmpty()) {
        this
    } else {
        val result = JSONObject()
        for (key in filterKeys) {
            if (this.has(key)) {
                result.put(key, this.get(key))
            }
        }
        result
    }

    return buildJsonObject {
        for (key in filtered.keys()) {
            val value = filtered.get(key)
            when (value) {
                is String -> put(key, JsonPrimitive(value))
                is Number -> put(key, JsonPrimitive(value))
                is Boolean -> put(key, JsonPrimitive(value))
                else -> put(key, JsonPrimitive(value.toString()))
            }
        }
    }
}
