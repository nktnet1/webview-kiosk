package uk.nktnet.webviewkiosk.mqtt.messages

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.json.JSONObject
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus

@Serializable
sealed interface MqttResponseMessage {
    val responseType: String
    val identifier: String? get() = null
    val appInstanceId: String
}

@Serializable
data class MqttStatusResponse(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val responseType: String = "status",
    override val identifier: String? = null,
    override val appInstanceId: String,
    val data: WebviewKioskStatus,
) : MqttResponseMessage

@Serializable
data class MqttSettingsResponse(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val responseType: String = "settings",
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
